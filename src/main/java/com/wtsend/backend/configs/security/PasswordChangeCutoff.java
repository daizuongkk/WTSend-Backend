package com.wtsend.backend.configs.security;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * When each user last changed their password, which is the line every
 * credential issued before it falls behind.
 *
 * <p>
 * It exists as its own component because two very different places need the
 * same fact and neither should reach into the other: {@code JwtConfig} consults
 * it on access tokens, and {@code RefreshTokenService} consults it when
 * rotating. Putting it here keeps JPA out of the security configuration -- a
 * {@code JwtDecoder} that depended on a service carrying a
 * {@code UserRepository} would drag the entity manager into bean creation for
 * the filter chain.
 *
 * <p>
 * The key name is also hard-coded in the rotation Lua script, which cannot
 * import anything; the two have to be changed together.
 */
@Component
@RequiredArgsConstructor
public class PasswordChangeCutoff {

	static final String KEY_PREFIX = "pwdchg:";

	private final StringRedisTemplate redis;

	/**
	 * Marks every credential issued before now as dead for this user.
	 *
	 * <p>
	 * No TTL: it has to outlive any token it is meant to reject, and the longest
	 * of those is the refresh-token lifetime. One small key per user who has ever
	 * changed a password is a price worth paying for not having to reason about
	 * whether the marker expired before the credential it was guarding against.
	 */
	public void markNow(String userId) {
		redis.opsForValue().set(KEY_PREFIX + userId, String.valueOf(Instant.now().getEpochSecond()));
	}

	/** Empty when this user has never changed their password. */
	public Optional<Instant> forUser(String userId) {
		String value = redis.opsForValue().get(KEY_PREFIX + userId);
		return value == null ? Optional.empty() : Optional.of(Instant.ofEpochSecond(Long.parseLong(value)));
	}

	/**
	 * Whether a credential issued at {@code issuedAt} is still acceptable.
	 *
	 * <p>
	 * The comparison is strict, so a credential issued in the same second as the
	 * change survives. That is deliberate: {@code changePassword} writes the
	 * marker and then hands the caller a fresh pair, and both JWT {@code iat} and
	 * this marker are second-granularity, so a non-strict comparison would
	 * invalidate the very tokens it had just issued. The cost is a one-second
	 * window in which a credential minted at the same second as the change also
	 * survives.
	 */
	public boolean stillValid(String userId, Instant issuedAt) {
		if (issuedAt == null)
			return true;
		return forUser(userId)
				.map(changedAt -> !issuedAt.isBefore(changedAt))
				.orElse(true);
	}
}
