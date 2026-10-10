package com.wtsend.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.corundumstudio.socketio.SocketIOServer;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.configs.security.PasswordChangeCutoff;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.RefreshTokenService.IssuedRefreshToken;

/**
 * Rotation against a real Redis, because the guarantee being tested is that
 * consume-and-reissue is ONE operation. A mocked store cannot show that; a Lua
 * script either runs atomically or it does not.
 */
@SpringBootTest
class RefreshTokenRotationTest {

	/** Keeps the real server off port 9092 while this context is up. */
	@MockitoBean
	private SocketIOServer server;

	@Autowired
	private RefreshTokenService refreshTokenService;
	@Autowired
	private PasswordChangeCutoff passwordChangeCutoff;
	@Autowired
	private StringRedisTemplate redis;
	@Autowired
	private UserRepository userRepository;

	private User user;
	private final List<String> createdUserIds = new ArrayList<>();
	private final List<String> touchedKeys = new ArrayList<>();

	@BeforeEach
	void setUp() {
		user = userRepository.save(User.builder()
				.email("rotation-" + System.nanoTime() + "@example.com")
				.displayName("Rotation").emailVerified(true).build());
		createdUserIds.add(user.getId());
	}

	@AfterEach
	void removeWhatThisTestWrote() {
		touchedKeys.forEach(redis::delete);
		createdUserIds.forEach(id -> {
			redis.delete("pwdchg:" + id);
			userRepository.deleteById(id);
		});
		touchedKeys.clear();
		createdUserIds.clear();
	}

	private IssuedRefreshToken issue() {
		IssuedRefreshToken issued = refreshTokenService.create(user);
		touchedKeys.add("rt:" + issued.token());
		touchedKeys.add("rtused:" + issued.token());
		touchedKeys.add("rtfam:" + issued.familyId());
		return issued;
	}

	@Test
	void rotatingOnceHandsBackTheUserAndASuccessor() {
		IssuedRefreshToken first = issue();

		var rotated = refreshTokenService.rotate(first.token());
		touchedKeys.add("rt:" + rotated.refreshToken().token());
		touchedKeys.add("rtused:" + rotated.refreshToken().token());

		assertThat(rotated.user().getId()).isEqualTo(user.getId());
		assertThat(rotated.refreshToken().token()).isNotEqualTo(first.token());
		// Same chain: that is what makes replay detection able to revoke the
		// descendants of a stolen token.
		assertThat(rotated.refreshToken().familyId()).isEqualTo(first.familyId());
	}

	@Test
	void theSameTokenCannotBeSpentTwice() {
		IssuedRefreshToken first = issue();

		var rotated = refreshTokenService.rotate(first.token());
		touchedKeys.add("rt:" + rotated.refreshToken().token());
		touchedKeys.add("rtused:" + rotated.refreshToken().token());

		// Read-then-delete let both of two concurrent calls through, and one token
		// became two independent live chains. Consume is one operation now.
		assertThatThrownBy(() -> refreshTokenService.rotate(first.token()))
				.isInstanceOf(AppException.class)
				.extracting(e -> ((AppException) e).getErrorCode())
				.isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
	}

	@Test
	void replayingAConsumedTokenKillsTheWholeChain() {
		IssuedRefreshToken first = issue();
		var second = refreshTokenService.rotate(first.token()).refreshToken();
		touchedKeys.add("rt:" + second.token());
		touchedKeys.add("rtused:" + second.token());

		// The attacker replays the token they stole. The victim's live successor
		// has to die with it -- otherwise the thief is merely inconvenienced and
		// the victim never finds out.
		assertThatThrownBy(() -> refreshTokenService.rotate(first.token()))
				.isInstanceOf(AppException.class);

		assertThat(redis.hasKey("rt:" + second.token())).isFalse();
		assertThatThrownBy(() -> refreshTokenService.rotate(second.token()))
				.isInstanceOf(AppException.class)
				.extracting(e -> ((AppException) e).getErrorCode())
				.isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
	}

	@Test
	void anUnknownTokenIsRefusedWithoutRevokingAnything() {
		IssuedRefreshToken live = issue();

		assertThatThrownBy(() -> refreshTokenService.rotate("not-a-token"))
				.isInstanceOf(AppException.class);

		// A guess must not be able to take somebody else's session down.
		assertThat(redis.hasKey("rt:" + live.token())).isTrue();
	}

	@Test
	void aPasswordChangeStopsEveryRefreshTokenIssuedBeforeIt() {
		IssuedRefreshToken before = issue();

		// A second ahead, written directly, because the cutoff comparison is
		// strict to the second: in real use the change happens seconds or minutes
		// after the token was issued, but a test that calls both in the same
		// millisecond would land inside the one-second window the strictness
		// deliberately leaves open (see theCutoffRejectsOlderCredentials...).
		redis.opsForValue().set("pwdchg:" + user.getId(),
				String.valueOf(java.time.Instant.now().getEpochSecond() + 1));

		// No enumeration happened -- the marker alone refuses it, which is how
		// this also covers tokens on devices this process never saw.
		assertThatThrownBy(() -> refreshTokenService.rotate(before.token()))
				.isInstanceOf(AppException.class)
				.extracting(e -> ((AppException) e).getErrorCode())
				.isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
	}

	@Test
	void aTokenIssuedAfterTheChangeStillWorks() {
		refreshTokenService.revokeEverythingIssuedBefore(user.getId());

		// changePassword writes the marker and then hands the caller a fresh
		// pair; that pair has to survive or the user is signed out by their own
		// password change.
		IssuedRefreshToken after = issue();

		var rotated = refreshTokenService.rotate(after.token());
		touchedKeys.add("rt:" + rotated.refreshToken().token());
		touchedKeys.add("rtused:" + rotated.refreshToken().token());
		assertThat(rotated.user().getId()).isEqualTo(user.getId());
	}

	@Test
	void theCutoffRejectsOlderCredentialsAndAcceptsTheSameSecond() {
		assertThat(passwordChangeCutoff.forUser(user.getId())).isEmpty();
		assertThat(passwordChangeCutoff.stillValid(user.getId(), java.time.Instant.now())).isTrue();

		passwordChangeCutoff.markNow(user.getId());
		java.time.Instant changedAt = passwordChangeCutoff.forUser(user.getId()).orElseThrow();

		assertThat(passwordChangeCutoff.stillValid(user.getId(), changedAt.minusSeconds(1))).isFalse();
		// Same second survives, on purpose: JWT iat and this marker are both
		// second-granularity, so a strict comparison is what keeps changePassword
		// from invalidating the token it just minted.
		assertThat(passwordChangeCutoff.stillValid(user.getId(), changedAt)).isTrue();
	}
}
