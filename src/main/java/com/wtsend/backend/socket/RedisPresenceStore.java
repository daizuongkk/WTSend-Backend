package com.wtsend.backend.socket;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Who is online, shared by every instance.
 *
 * <p>
 * Replaces the in-process map this used to be: with two instances each one
 * reported only its own half of the users as online, so the list a client got
 * depended on which node it happened to connect to.
 *
 * <p>
 * Two keys per user:
 *
 * <pre>
 * presence:sockets:{userId}   SET of "{instanceId}:{sessionId}" -- one entry per live socket
 * presence:online             ZSET userId -> last heartbeat, as epoch seconds
 * </pre>
 *
 * <p>
 * The ZSET score is what makes a crash survivable. An instance killed with -9
 * runs no disconnect handler, so its entries are never removed; instead every
 * instance re-stamps the score of its own users every
 * {@link PresenceHeartbeat#INTERVAL}, and a read ignores anything older than
 * {@link #ONLINE_TTL}. Nothing has to notice the crash for the users to drop
 * off, which is the one thing a cleanup job could not have guaranteed.
 *
 * <p>
 * add and remove report whether the user's presence changed, not merely whether
 * a socket did -- a second tab must not announce an arrival and closing one of
 * two tabs must not announce a departure. The counting is done inside Lua
 * because the decision reads a value it just wrote: two sockets closing at the
 * same moment on different instances would otherwise both see a non-empty set
 * and neither would report the user offline.
 */
@Component
@RequiredArgsConstructor
public class RedisPresenceStore {

  /** A user whose heartbeat is older than this is not reported online. */
  static final Duration ONLINE_TTL = Duration.ofSeconds(90);

  /**
   * Socket sets outlive their heartbeat window so a brief Redis or scheduler
   * hiccup does not drop a live socket's entry, but still expire on their own
   * if the instance holding them dies.
   */
  private static final Duration SOCKETS_TTL = Duration.ofSeconds(180);

  private static final String ONLINE_KEY = "presence:online";
  private static final String SOCKETS_KEY_PREFIX = "presence:sockets:";

  /**
   * KEYS: sockets, online. ARGV: member, now, socketsTtl, userId, visible,
   * staleBefore. Returns 1 if this socket brought the user online.
   *
   * <p>
   * The socket is recorded either way; only the entry other people read is
   * conditional. A hidden user still needs their sockets counted, or closing
   * one of their two tabs would look like their last one.
   *
   * <p>
   * Arrival is decided by whether the user had a <em>fresh</em> entry in the
   * online list -- the same thing readers look at -- and deliberately not by
   * the socket count. An instance killed with -9 leaves socket members nobody
   * will ever remove, and counting those made a returning user look like
   * someone opening a second tab: no arrival was announced, and every page
   * already open went on showing them offline until it was reloaded.
   */
  private static final RedisScript<Long> ADD = new DefaultRedisScript<>("""
      local score = redis.call('ZSCORE', KEYS[2], ARGV[4])
      local wasOnline = score and tonumber(score) >= tonumber(ARGV[6])
      redis.call('SADD', KEYS[1], ARGV[1])
      redis.call('EXPIRE', KEYS[1], ARGV[3])
      if ARGV[5] == '1' then
        redis.call('ZADD', KEYS[2], ARGV[2], ARGV[4])
      end
      if wasOnline then
        return 0
      end
      return 1
      """, Long.class);

  /**
   * KEYS: online. ARGV: staleBefore. Returns the user ids it removed.
   *
   * <p>
   * Reading and removing in one script matters with more than one instance:
   * they all run this, and only the one whose call actually removed the
   * entries gets them back to announce, so a departure is announced once.
   */
  @SuppressWarnings("rawtypes")
  private static final RedisScript<List> PRUNE = new DefaultRedisScript<>("""
      local stale = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
      redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
      return stale
      """, List.class);

  /**
   * KEYS: sockets, online. ARGV: member, userId. Returns 1 if that was the last
   * socket.
   */
  private static final RedisScript<Long> REMOVE = new DefaultRedisScript<>("""
      redis.call('SREM', KEYS[1], ARGV[1])
      if redis.call('SCARD', KEYS[1]) == 0 then
        redis.call('DEL', KEYS[1])
        redis.call('ZREM', KEYS[2], ARGV[2])
        return 1
      end
      return 0
      """, Long.class);

  /**
   * KEYS: sockets, online. ARGV: now, socketsTtl, userId.
   *
   * <p>
   * {@code XX} only when the user is hidden, and that distinction is
   * load-bearing in both directions. For a hidden user it is what keeps
   * {@link #hide(String)} from being undone by the next heartbeat 30 seconds
   * later. For a visible one it was a trap: once an entry left
   * {@code presence:online} -- which a single heartbeat later than the 90s read
   * window is enough to do, since {@link #pruneStale()} removes it -- no
   * heartbeat could ever put it back, and every still-connected user stayed
   * invisible for the rest of their socket's life. A heartbeat's job is
   * liveness, and re-registering a user this instance is still holding a
   * socket for is exactly that.
   *
   * <p>
   * KEYS: sockets, online. ARGV: now, socketsTtl, userId, "1" when visible.
   */
  private static final RedisScript<Long> TOUCH = new DefaultRedisScript<>("""
      redis.call('EXPIRE', KEYS[1], ARGV[2])
      if ARGV[4] == '1' then
        redis.call('ZADD', KEYS[2], ARGV[1], ARGV[3])
      else
        redis.call('ZADD', KEYS[2], 'XX', ARGV[1], ARGV[3])
      end
      return 1
      """, Long.class);

  /**
   * KEYS: sockets, online. ARGV: now, userId. Returns 1 if the user is now
   * listed.
   */
  private static final RedisScript<Long> SHOW = new DefaultRedisScript<>("""
      if redis.call('SCARD', KEYS[1]) == 0 then
        return 0
      end
      redis.call('ZADD', KEYS[2], ARGV[1], ARGV[2])
      return 1
      """, Long.class);

  private final StringRedisTemplate redis;
  private final InstanceId instanceId;

  /**
   * @param visible whether other people may see this user, i.e.
   *                {@code User.showOnlineStatus}
   * @return true if this socket brought the user online anywhere in the
   *         cluster. Only meaningful for a visible user -- a hidden one is
   *         never in the list, so every socket of theirs looks like an
   *         arrival, and the caller does not announce it either way.
   */
  public boolean add(UUID userId, UUID socketId, boolean visible) {
    Long cameOnline = redis.execute(ADD, keys(userId),
        member(socketId), now(), ttlSeconds(SOCKETS_TTL), userId,
        visible ? "1" : "0", staleBefore());
    return cameOnline != null && cameOnline == 1L;
  }

  /**
   * Stops other people seeing this user as online, from any instance -- the
   * entry is keyed by user, so the instance holding their sockets does not
   * have to be the one handling the request.
   */
  public void hide(UUID userId) {
    redis.opsForZSet().remove(ONLINE_KEY, userId);
  }

  /**
   * Lets other people see this user again, if they are actually connected
   * somewhere.
   *
   * @return true if the user is now listed, false if they had no live socket
   */
  public boolean show(UUID userId) {
    Long listed = redis.execute(SHOW, keys(userId), now(), userId);
    return listed != null && listed == 1L;
  }

  /** @return true if this was the user's last socket anywhere in the cluster */
  public boolean remove(UUID userId, UUID socketId) {
    Long wentOffline = redis.execute(REMOVE, keys(userId), member(socketId), userId);
    return wentOffline != null && wentOffline == 1L;
  }

  /**
   * Re-stamps the heartbeat for a user this instance still holds a socket for.
   *
   * @param visible {@code User.showOnlineStatus}. False means refresh only an
   *                entry that already exists, so a hidden user is not put back
   *                in the list by their own heartbeat.
   */
  public void touch(UUID userId, boolean visible) {
    redis.execute(TOUCH, keys(userId), now(), ttlSeconds(SOCKETS_TTL), userId,
        visible ? "1" : "0");
  }

  public Set<String> onlineUserIds() {
    Set<String> ids = redis.opsForZSet().rangeByScore(ONLINE_KEY,
        Instant.now().minus(ONLINE_TTL).getEpochSecond(), Double.POSITIVE_INFINITY);
    return ids == null ? Set.of() : ids;
  }

  /**
   * Drops the heartbeats nobody has refreshed and says whose they were.
   *
   * <p>
   * The caller has to announce them. It is not hygiene: a page that is
   * already open never re-reads the online list, so when an instance dies
   * without running its disconnect handlers, removing its users here is the
   * only moment anyone could be told they are gone. Silently deleting the
   * rows left every open page showing a vanished user as online for as long
   * as it stayed open.
   *
   * @return the user ids removed, which this instance is now responsible for
   *         broadcasting as offline
   */
  @SuppressWarnings("unchecked")
  List<UUID> pruneStale() {
    List<UUID> removed = redis.execute(PRUNE, List.of(ONLINE_KEY), staleBefore());
    return removed == null ? List.of() : removed;
  }

  private List<String> keys(UUID userId) {
    return List.of(SOCKETS_KEY_PREFIX + userId, ONLINE_KEY);
  }

  /**
   * The instance id is part of the member so an entry can be attributed to the
   * process that created it; session ids alone are unique but anonymous.
   */
  private String member(UUID socketId) {
    return instanceId.value() + ":" + socketId;
  }

  private String now() {
    return String.valueOf(Instant.now().getEpochSecond());
  }

  /** Heartbeats older than this are not online, whoever wrote them. */
  private String staleBefore() {
    return String.valueOf(Instant.now().minus(ONLINE_TTL).getEpochSecond());
  }

  private String ttlSeconds(Duration duration) {
    return String.valueOf(duration.toSeconds());
  }
}
