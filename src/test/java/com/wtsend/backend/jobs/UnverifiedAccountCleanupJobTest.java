package com.wtsend.backend.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.corundumstudio.socketio.SocketIOServer;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.UserRepository;

/**
 * Registering with an address you do not own and walking away used to occupy
 * that address forever: the real owner could never sign up, because the email
 * was already taken by a row nobody controlled.
 */
@SpringBootTest
class UnverifiedAccountCleanupJobTest {

	/** Keeps the real server off port 9092 while this context is up. */
	@MockitoBean
	private SocketIOServer server;

	@Autowired
	private UnverifiedAccountCleanupJob job;
	@Autowired
	private UserRepository userRepository;

	/**
	 * createdAt is set by auditing on persist, so the age has to be backdated
	 * afterwards -- a save with a stale timestamp would just be overwritten.
	 */
	private String persistUser(boolean verified, Instant createdAt) {
		User user = userRepository.save(User.builder()
				.email("cleanup-" + System.nanoTime() + "@example.com")
				.displayName("Cleanup")
				.emailVerified(verified)
				.build());
		ReflectionTestUtils.setField(user, "createdAt", createdAt);
		return userRepository.save(user).getId();
	}

	@Test
	void removesARegistrationLeftUnverifiedPastTheGracePeriod() {
		String abandoned = persistUser(false, Instant.now().minus(UnverifiedAccountCleanupJob.GRACE_PERIOD).minusSeconds(60));

		job.sweep();

		assertThat(userRepository.findById(abandoned)).isEmpty();
	}

	@Test
	void keepsARegistrationStillInsideTheGracePeriod() {
		// Someone who signed up an hour ago and has not clicked the link yet.
		String recent = persistUser(false, Instant.now().minusSeconds(3600));

		job.sweep();

		assertThat(userRepository.findById(recent)).isPresent();
	}

	@Test
	void keepsAVerifiedAccountNoMatterHowOld() {
		// The whole reason the email address cannot be changed: if it could,
		// emailVerified = false would also mean "verified once, changing address",
		// and this sweep would delete established accounts.
		String established = persistUser(true, Instant.now().minus(UnverifiedAccountCleanupJob.GRACE_PERIOD).minusSeconds(86_400));

		job.sweep();

		assertThat(userRepository.findById(established)).isPresent();
	}

	@Test
	void aSecondSweepIsHarmless() {
		persistUser(false, Instant.now().minus(UnverifiedAccountCleanupJob.GRACE_PERIOD).minusSeconds(60));

		int first = job.sweep();
		int second = job.sweep();

		assertThat(first).isPositive();
		assertThat(second).isZero();
	}
}
