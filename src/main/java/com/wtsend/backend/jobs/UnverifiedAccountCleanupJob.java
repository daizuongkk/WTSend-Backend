package com.wtsend.backend.jobs;

import java.time.Duration;
import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.wtsend.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Removes accounts that registered and never verified their email.
 *
 * <p>
 * Without this, registering with somebody else's address and walking away
 * leaves that address permanently occupied: the real owner can never sign up,
 * because the email is already taken by a row nobody controls.
 *
 * <p>
 * Deleting is safe precisely because an unverified account can do nothing.
 * SecurityConfig gates all of /api/** behind EMAIL_VERIFIED, leaving only
 * /api/users/me and /api/send-verify-email, so there are no messages, friends
 * or conversations to orphan -- no row anywhere references these users.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnverifiedAccountCleanupJob {

	/** How long a registration may sit unverified before it is removed. */
	public static final Duration GRACE_PERIOD = Duration.ofHours(24);

	private final UserRepository userRepository;

	@Scheduled(cron = "${app.cleanup.unverified-accounts.cron}")
	@SchedulerLock(name = "unverified-account-cleanup", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
	@Transactional
	public void deleteAbandonedRegistrations() {
		int deleted = sweep();
		if (deleted > 0)
			log.info("Deleted {} registrations left unverified for more than {}", deleted, GRACE_PERIOD);
	}

	/** Separated so a test can drive one sweep without waiting on the cron. */
	@Transactional
	public int sweep() {
		return userRepository.deleteUnverifiedRegisteredBefore(Instant.now().minus(GRACE_PERIOD));
	}
}
