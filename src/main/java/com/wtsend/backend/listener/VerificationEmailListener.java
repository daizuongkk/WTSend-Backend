package com.wtsend.backend.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.wtsend.backend.event.VerificationEmailRequestedEvent;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends the verification link outside the caller's transaction.
 *
 * <p>
 * {@code AuthService.signUp} is {@code @Transactional} and used to call Resend
 * inline, holding a pooled database connection for the whole of a third-party
 * HTTP request -- enough to exhaust the pool under any real load. Running after
 * commit releases the connection first.
 *
 * <p>
 * {@code fallbackExecution = true} because {@code signIn} publishes the same
 * event with no transaction open; without it Spring would discard the event and
 * unverified users would stop being sent a link at all.
 *
 * <p>
 * Failures are logged, not rethrown. Two reasons: after commit there is nothing
 * left to roll back, and {@code sendVerifyLink} throws
 * {@code VERIFY_EMAIL_COOLDOWN} within 30 seconds of the previous send -- which
 * used to escape through {@code signIn} and fail the login of an unverified
 * user who simply tried twice. An account that never receives its link is not
 * stranded: signing in requests another, and an unverified account is deleted
 * after 24 hours.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VerificationEmailListener {

	private final UserRepository userRepository;
	private final EmailService emailService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onVerificationEmailRequested(VerificationEmailRequestedEvent event) {
		try {
			userRepository.findById(event.getUserId())
					.ifPresentOrElse(
							emailService::sendVerifyLink,
							() -> log.warn("No user {} to send a verification link to", event.getUserId()));
		} catch (Exception e) {
			log.error("Failed to send a verification link to user {}", event.getUserId(), e);
		}
	}
}
