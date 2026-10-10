package com.wtsend.backend.configs.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.corundumstudio.socketio.SocketIOServer;
import com.wtsend.backend.configs.security.AuthRateLimit.RateLimitedException;

/**
 * Against a real Redis, because what is being tested is the counter: a mocked
 * template would only prove the method was called.
 */
@SpringBootTest
class AuthRateLimitTest {

	/** Keeps the real server off port 9092 while this context is up. */
	@MockitoBean
	private SocketIOServer server;

	@Autowired
	private AuthRateLimit rateLimit;
	@Autowired
	private StringRedisTemplate redis;

	private final List<String> touchedKeys = new ArrayList<>();

	private String email(String local) {
		String address = local + "@example.com";
		touchedKeys.add("rl:login:email:" + address);
		touchedKeys.add("rl:reset:email:" + address);
		return address;
	}

	private String ip(String address) {
		touchedKeys.add("rl:login:ip:" + address);
		touchedKeys.add("rl:reset:ip:" + address);
		return address;
	}

	@AfterEach
	void removeWhatThisTestWrote() {
		touchedKeys.forEach(redis::delete);
		touchedKeys.clear();
	}

	@Test
	void theTenthFailedLoginIsAllowedAndTheEleventhIsNot() {
		String address = email("login-limit");
		String from = ip("10.0.0.1");

		for (int attempt = 1; attempt <= 10; attempt++) {
			int n = attempt;
			assertThatCode(() -> rateLimit.login(from, address))
					.as("attempt %d", n)
					.doesNotThrowAnyException();
		}

		assertThatThrownBy(() -> rateLimit.login(from, address))
				.isInstanceOf(RateLimitedException.class)
				.extracting(e -> ((RateLimitedException) e).getRetryAfterSeconds())
				// Reported, not guessed: the client needs to know how long to wait.
				.satisfies(seconds -> assertThat((Long) seconds).isPositive());
	}

	@Test
	void signingInSuccessfullyForgetsTheFailuresForThatAddress() {
		String address = email("login-reset");
		String from = ip("10.0.0.2");

		for (int attempt = 1; attempt <= 10; attempt++)
			rateLimit.login(from, address);

		// Which is what makes the email counter a count of CONSECUTIVE failures.
		// Without it, someone who mistyped their password ten times over a
		// fortnight would be locked out on the eleventh correct attempt.
		rateLimit.loginSucceeded(address);

		assertThatCode(() -> rateLimit.login(from, address)).doesNotThrowAnyException();
	}

	@Test
	void theIpCounterIsNotClearedByASuccess() {
		String from = ip("10.0.0.3");

		// Thirty attempts from one host, spread over thirty different accounts,
		// each one "successful". The email counters are useless against this.
		for (int attempt = 1; attempt <= 30; attempt++) {
			String address = email("ip-limit-" + attempt);
			rateLimit.login(from, address);
			rateLimit.loginSucceeded(address);
		}

		assertThatThrownBy(() -> rateLimit.login(from, email("ip-limit-31")))
				.isInstanceOf(RateLimitedException.class);
	}

	@Test
	void theAddressIsNormalisedSoRespellingItIsNotAFreshAllowance() {
		String address = email("case-limit");
		String from = ip("10.0.0.4");

		for (int attempt = 1; attempt <= 10; attempt++)
			rateLimit.login(from, "  " + address.toUpperCase() + " ");

		assertThatThrownBy(() -> rateLimit.login(from, address))
				.isInstanceOf(RateLimitedException.class);
	}

	@Test
	void passwordResetIsLimitedFarMoreTightlyThanLogin() {
		String address = email("reset-limit");
		String from = ip("10.0.0.5");

		for (int attempt = 1; attempt <= 5; attempt++)
			rateLimit.passwordReset(from, address);

		// Five an hour: this endpoint answers the same way for every address, so
		// uncounted it is a way to post mail to anybody at this app's expense.
		assertThatThrownBy(() -> rateLimit.passwordReset(from, address))
				.isInstanceOf(RateLimitedException.class);

		// And it is its own counter -- a used-up reset allowance must not lock
		// the same person out of signing in.
		assertThatCode(() -> rateLimit.login(from, address)).doesNotThrowAnyException();
	}

	@Test
	void theCounterAlwaysCarriesATtl() {
		String address = email("ttl-check");
		rateLimit.login(ip("10.0.0.6"), address);

		// INCR and EXPIRE are one script precisely because of this: a key left
		// without a TTL never resets and locks its subject out for good.
		assertThat(redis.getExpire("rl:login:email:" + address)).isPositive();
	}
}
