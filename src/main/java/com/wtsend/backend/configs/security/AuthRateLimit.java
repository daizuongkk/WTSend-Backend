package com.wtsend.backend.configs.security;

import java.time.Duration;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.wtsend.backend.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fixed-window counters for the two endpoints anybody can hammer without an
 * account.
 *
 * <p>
 * Only those two. {@code /auth/login} is the one place a password can be
 * guessed, and {@code /auth/forgot-password} is the one place a stranger can
 * make this app send mail. Everything else under {@code /api/**} already needs
 * a verified account, which is a far tighter gate than a counter.
 *
 * <p>
 * Two counters per attempt, because one of them alone is useless: by email
 * stops one account being ground through a password list from a thousand
 * addresses, and by IP stops one host working through a thousand accounts. A
 * successful login clears the email counter, so in practice it counts
 * consecutive failures; the IP counter does not, so an attacker's successes are
 * not free either.
 *
 * <p>
 * The window is fixed rather than sliding. A fixed window lets up to double the
 * limit through across a boundary, which for these numbers is not worth a
 * sorted set and a cleanup policy.
 *
 * <h2>Redis being down lets the request through</h2>
 *
 * Deliberate, and logged. Redis is already required for refresh tokens, so an
 * outage means nobody can hold a session anyway -- refusing here on top would
 * only relabel that outage as "too many requests" and send everyone looking in
 * the wrong place.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthRateLimit {

	/** One counter: its key space, how many are allowed, and over what period. */
	private record Window(String prefix, int max, Duration period) {
	}

	private static final Window LOGIN_BY_EMAIL = new Window("rl:login:email:", 10, Duration.ofMinutes(15));
	private static final Window LOGIN_BY_IP = new Window("rl:login:ip:", 30, Duration.ofMinutes(15));
	private static final Window RESET_BY_EMAIL = new Window("rl:reset:email:", 5, Duration.ofHours(1));
	private static final Window RESET_BY_IP = new Window("rl:reset:ip:", 20, Duration.ofHours(1));

	/**
	 * Counts one hit and reports the seconds left if that put the key over its
	 * limit, or -1 if it did not.
	 *
	 * <p>
	 * INCR and EXPIRE in one script. Done as two calls, a crash between them
	 * leaves a key with no TTL, which never resets and locks the subject out for
	 * good.
	 *
	 * <p>
	 * KEYS: the counter. ARGV: period in seconds, max.
	 */
	private static final RedisScript<Long> HIT = new DefaultRedisScript<>("""
			local hits = redis.call('INCR', KEYS[1])
			if hits == 1 then
			  redis.call('EXPIRE', KEYS[1], ARGV[1])
			end
			if hits > tonumber(ARGV[2]) then
			  local ttl = redis.call('TTL', KEYS[1])
			  if ttl < 0 then ttl = tonumber(ARGV[1]) end
			  return ttl
			end
			return -1
			""", Long.class);

	private final StringRedisTemplate redis;

	/** @throws RateLimitedException if this attempt is over either limit */
	public void login(String ip, String email) {
		enforce(LOGIN_BY_EMAIL, email, LOGIN_BY_IP, ip);
	}

	/**
	 * Forgets the failures for this address. Called after a successful sign-in,
	 * which is what makes the email counter a count of consecutive failures
	 * rather than of attempts.
	 */
	public void loginSucceeded(String email) {
		quietly(() -> redis.delete(LOGIN_BY_EMAIL.prefix() + subject(email)));
	}

	/** @throws RateLimitedException if this attempt is over either limit */
	public void passwordReset(String ip, String email) {
		enforce(RESET_BY_EMAIL, email, RESET_BY_IP, ip);
	}

	/**
	 * Counts both windows and refuses if either is over.
	 *
	 * <p>
	 * Both are counted even when the first already refuses, so a caller cannot
	 * use the cheaper limit as a shield for the other.
	 */
	private void enforce(Window byEmail, String email, Window byIp, String ip) {
		long emailRetry = hit(byEmail, subject(email));
		long ipRetry = hit(byIp, subject(ip));

		long retryAfter = Math.max(emailRetry, ipRetry);
		if (retryAfter >= 0) {
			// No detail naming which of the two tripped: the caller learns only
			// that they must wait, not whether this address is worth attacking.
			log.warn("Rate limit hit on {} (retry after {}s)", byEmail.prefix(), retryAfter);
			throw new RateLimitedException(retryAfter);
		}
	}

	private long hit(Window window, String subject) {
		if (subject.isEmpty())
			return -1;

		try {
			Long retryAfter = redis.execute(HIT, List.of(window.prefix() + subject),
					String.valueOf(window.period().toSeconds()), String.valueOf(window.max()));
			return retryAfter == null ? -1 : retryAfter;
		} catch (DataAccessException e) {
			log.warn("Rate limit counter unavailable; letting the request through", e);
			return -1;
		}
	}

	/**
	 * Lower-cased and trimmed, so {@code  Foo@x.com} and {@code foo@x.com} share
	 * one counter instead of giving an attacker a fresh allowance per spelling.
	 */
	private static String subject(String value) {
		return value == null ? "" : value.trim().toLowerCase();
	}

	private void quietly(Runnable action) {
		try {
			action.run();
		} catch (DataAccessException e) {
			log.warn("Could not clear a rate limit counter", e);
		}
	}

	/** Carries the wait so the response can answer with a Retry-After. */
	public static class RateLimitedException extends RuntimeException {

		private final long retryAfterSeconds;

		RateLimitedException(long retryAfterSeconds) {
			super(ErrorCode.TOO_MANY_REQUESTS.getMessage());
			this.retryAfterSeconds = retryAfterSeconds;
		}

		public long getRetryAfterSeconds() {
			return retryAfterSeconds;
		}
	}
}
