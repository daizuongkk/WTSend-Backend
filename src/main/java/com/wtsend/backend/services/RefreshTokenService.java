package com.wtsend.backend.services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.configs.security.PasswordChangeCutoff;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Refresh tokens: issued, rotated once, and revoked as a family when one is
 * replayed long enough after it was spent.
 *
 * <p>
 * This manages its Redis keys directly instead of going through a
 * {@code @RedisHash} repository, and that is the whole reason it exists in this
 * shape. Rotation used to be {@code findById} followed by {@code deleteById} --
 * two round trips with nothing between them -- so two concurrent calls carrying
 * the same cookie both read the record before either delete landed, and one
 * token became two independent live chains. A repository cannot express
 * read-and-delete as one operation; a Lua script can.
 *
 * <h2>The grace window, and why it has to exist</h2>
 *
 * Making rotation atomic turned that old race into a worse one. Two refreshes
 * carrying the same cookie are completely ordinary -- two browser tabs both
 * restoring a session on load, React re-running an effect, a socket reconnect
 * overlapping a queued request -- and the second one is indistinguishable from
 * a stolen token. Treating it as theft revoked the family, which deletes the
 * successor the first call had just issued and handed the browser a cookie that
 * was already dead. One page load logged the user out, permanently.
 *
 * <p>
 * So a token spent within {@code jwt.refresh-token.replay-grace-seconds} gets a
 * second successor in the same family instead of killing it. Past that window
 * it is still treated as a replay and the family dies: a thief replaying a
 * token they captured is essentially never inside a ten-second window, while
 * two tabs of the same browser essentially always are.
 *
 * <h2>Keys</h2>
 *
 * <pre>
 * rt:{token}      "{userId}|{familyId}|{issuedAtEpochSeconds}"
 * rtused:{token}  "{userId}|{familyId}|{consumedAtEpochSeconds}" -- tombstone,
 *                 so a replay is distinguishable from a token that never
 *                 existed, and so the grace window can be measured
 * rtfam:{family}  SET of the live tokens in one chain
 * pwdchg:{userId} epoch seconds of the last password change
 * </pre>
 *
 * <p>
 * {@code pwdchg} does double duty. It is what {@code JwtConfig} checks to
 * reject access tokens issued before a password change, and it is also why
 * there is no user-to-tokens index here: a password change writes the marker
 * and every refresh token issued before it stops rotating, including ones on
 * devices this process never saw. The stale {@code rt:} keys simply age out.
 *
 * <p>
 * The scripts address keys they derive from a value they just read, so they
 * assume a single Redis rather than a cluster. That matches the rest of the app
 * -- presence and the socket relay assume the same.
 */
@Service
@Slf4j
public class RefreshTokenService {

  private static final String TOKEN_KEY = "rt:";
  private static final String USED_KEY = "rtused:";
  private static final String FAMILY_KEY = "rtfam:";

  /** What one rotation attempt turned out to be. */
  public enum Outcome {
    OK, INVALID, REUSED
  }

  /** The result of a rotation: on OK, who it belongs to and the successor. */
  public record Rotation(Outcome outcome, String userId, String token, String familyId) {
  }

  /** A freshly issued token and the chain it belongs to. */
  public record IssuedRefreshToken(String token, UUID familyId) {
  }

  /**
   * Consumes one token and issues its successor in the same family, as a single
   * operation -- or, for a token spent moments ago, issues a second successor
   * rather than treating the duplicate as theft.
   *
   * <p>
   * KEYS: rt:{token}, rtused:{token}. ARGV: token, newToken, ttlSeconds, now,
   * graceSeconds.
   */
  private static final RedisScript<List> ROTATE = new DefaultRedisScript<>("""
      local token, successor = ARGV[1], ARGV[2]
      local ttl, now, grace = tonumber(ARGV[3]), tonumber(ARGV[4]), tonumber(ARGV[5])

      -- "{userId}|{familyId}|{epochSeconds}", the shape of both rt: and rtused:
      local function parts(value)
        local first = string.find(value, '|', 1, true)
        local second = string.find(value, '|', first + 1, true)
        return string.sub(value, 1, first - 1),
               string.sub(value, first + 1, second - 1),
               tonumber(string.sub(value, second + 1))
      end

      -- A password change invalidates everything issued before it, wherever it
      -- lives, without this process having to enumerate the user's tokens.
      local function supersededByPasswordChange(userId, stamp)
        return stamp < tonumber(redis.call('GET', 'pwdchg:' .. userId) or '0')
      end

      local function issue(userId, family)
        redis.call('SET', 'rt:' .. successor, userId .. '|' .. family .. '|' .. now, 'EX', ttl)
        redis.call('SADD', 'rtfam:' .. family, successor)
        redis.call('EXPIRE', 'rtfam:' .. family, ttl)
        return {'OK', userId, family}
      end

      local stored = redis.call('GET', KEYS[1])

      if not stored then
        local spent = redis.call('GET', KEYS[2])
        if not spent then
          return {'INVALID', '', ''}
        end

        local userId, family, consumedAt = parts(spent)

        -- Inside the window this is two tabs, not a thief. Strictly inside:
        -- these stamps are whole seconds, so `>` would make a grace of 0 still
        -- forgive anything landing in the same second, and 0 has to mean none.
        --
        -- The password check uses consumedAt rather than the original issuedAt,
        -- which the tombstone does not carry: at most the grace window of
        -- slack, and the successor issued below carries `now`, so the next
        -- rotation is exact.
        if now - consumedAt >= grace or supersededByPasswordChange(userId, consumedAt) then
          return {'REUSED', '', family}
        end

        return issue(userId, family)
      end

      local userId, family, issuedAt = parts(stored)
      if supersededByPasswordChange(userId, issuedAt) then
        redis.call('DEL', KEYS[1])
        return {'INVALID', '', ''}
      end

      redis.call('DEL', KEYS[1])
      redis.call('SET', KEYS[2], userId .. '|' .. family .. '|' .. now, 'EX', ttl)
      redis.call('SREM', 'rtfam:' .. family, token)
      return issue(userId, family)
      """, List.class);

  /** KEYS: rtfam:{family}. Returns how many tokens it removed. */
  private static final RedisScript<Long> REVOKE_FAMILY = new DefaultRedisScript<>("""
      local members = redis.call('SMEMBERS', KEYS[1])
      for i = 1, #members do
        redis.call('DEL', 'rt:' .. members[i])
        redis.call('DEL', 'rtused:' .. members[i])
      end
      redis.call('DEL', KEYS[1])
      return #members
      """, Long.class);

  @Value("${jwt.refresh-token.ttl}")
  private Long refreshTokenTtlDays;

  /**
   * How long after a token is spent a second presentation of it still counts as
   * a duplicate rather than a replay. Seconds, and deliberately short: it has
   * to cover two tabs loading together, not a stolen cookie.
   */
  @Value("${jwt.refresh-token.replay-grace-seconds:10}")
  private long replayGraceSeconds;

  private final StringRedisTemplate redis;
  private final UserRepository userRepo;
  private final PasswordChangeCutoff passwordChangeCutoff;

  public RefreshTokenService(StringRedisTemplate redis, UserRepository userRepo,
      PasswordChangeCutoff passwordChangeCutoff) {
    this.redis = redis;
    this.userRepo = userRepo;
    this.passwordChangeCutoff = passwordChangeCutoff;
  }

  private long ttlSeconds() {
    return Duration.ofDays(refreshTokenTtlDays).toSeconds();
  }

  /** Starts a new chain for this user. */
  public IssuedRefreshToken create(User user) {
    return issue(user.getId(), UUID.randomUUID());
  }

  private IssuedRefreshToken issue(UUID userId, UUID familyId) {
    String token = UUID.randomUUID().toString();
    long ttl = ttlSeconds();

    redis.opsForValue().set(TOKEN_KEY + token,
        "%s|%s|%d".formatted(userId, familyId, Instant.now().getEpochSecond()),
        Duration.ofSeconds(ttl));
    redis.opsForSet().add(FAMILY_KEY + familyId, token);
    redis.expire(FAMILY_KEY + familyId, Duration.ofSeconds(ttl));

    return new IssuedRefreshToken(token, familyId);
  }

  /**
   * Rotates a token and returns the user it belonged to plus the successor.
   *
   * <p>
   * A token presented again within the grace window gets its own successor, so
   * two tabs restoring the same session do not fight. Later than that it is a
   * replay: the whole family is revoked and the call then fails like any
   * invalid one, so the caller learns nothing about having tripped the
   * detection -- the only one who should be able to tell is whoever reads the
   * log.
   */
  public RotatedSession rotate(String token) {
    if (token == null || token.isBlank())
      throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);

    String successor = UUID.randomUUID().toString();
    @SuppressWarnings("unchecked")
    List<UUID> result = redis.execute(ROTATE,
        List.of(TOKEN_KEY + token, USED_KEY + token),
        token, successor, String.valueOf(ttlSeconds()),
        String.valueOf(Instant.now().getEpochSecond()),
        String.valueOf(replayGraceSeconds));

    Outcome outcome = result == null || result.isEmpty()
        ? Outcome.INVALID
        : Outcome.valueOf(result.get(0).toString());

    if (outcome == Outcome.REUSED) {
      UUID family = result.get(2);
      Long revoked = redis.execute(REVOKE_FAMILY, List.of(FAMILY_KEY + family));
      log.warn("Refresh token replayed; revoked {} token(s) in family {}", revoked, family);
      throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID).withDetail("replayed family=" + family);
    }

    if (outcome == Outcome.INVALID)
      throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);

    UUID userId = result.get(1);
    User user = userRepo.findById(userId)
        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)
            .withDetail("id=" + userId + " (referenced by a live refresh token)"));

    return new RotatedSession(user, new IssuedRefreshToken(successor, result.get(2)));
  }

  /** The user a rotation belonged to, and the token that replaces the old one. */
  public record RotatedSession(User user, IssuedRefreshToken refreshToken) {
  }

  /**
   * Deletes the presented token only, leaving the user's other sessions alone.
   * Idempotent: revoking an already-gone token is not an error.
   */
  public void revoke(String token) {
    if (token == null || token.isBlank())
      return;

    redis.delete(TOKEN_KEY + token);
  }

  /**
   * Marks every credential issued before now as dead for this user, refresh
   * and access alike.
   *
   * <p>
   * Replaces enumerating and deleting the user's refresh tokens, and reaches
   * further than that ever could: access tokens are stateless, so
   * {@link PasswordChangeCutoff} is what {@code JwtConfig} consults to reject
   * ones minted before the change. The refresh tokens issued earlier are left
   * to age out -- the rotation script refuses them, so they are inert.
   */
  public void revokeEverythingIssuedBefore(UUID userId) {
    passwordChangeCutoff.markNow(userId);
  }
}
