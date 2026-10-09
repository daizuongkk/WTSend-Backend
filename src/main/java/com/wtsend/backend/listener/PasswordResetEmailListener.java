package com.wtsend.backend.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.wtsend.backend.event.PasswordResetRequestedEvent;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends the reset link off the request's transaction, the same shape as
 * {@link VerificationEmailListener} -- see that class for why the Resend call
 * must not sit inside one.
 *
 * <p>
 * {@code fallbackExecution} because forgotPassword is a read followed by a mail
 * send and has no transaction of its own.
 *
 * <p>
 * Failures are logged rather than rethrown, including the 30-second cooldown.
 * Letting a cooldown escape would turn repeated clicks on "forgot password"
 * into an error, and the response is deliberately the same either way.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetEmailListener {

	private final UserRepository userRepository;
	private final EmailService emailService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
		try {
			userRepository.findById(event.getUserId())
					.ifPresentOrElse(
							emailService::sendPasswordResetLink,
							() -> log.warn("No user {} to send a reset link to", event.getUserId()));
		} catch (Exception e) {
			log.error("Failed to send a password reset link to user {}", event.getUserId(), e);
		}
	}
}
