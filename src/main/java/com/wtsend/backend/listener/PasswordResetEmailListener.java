package com.wtsend.backend.listener;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.wtsend.backend.event.PasswordResetRequestedEvent;
import com.wtsend.backend.messaging.KafkaEventPublisher;
import com.wtsend.backend.messaging.event.EmailRequestedEvent.EmailKind;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hands the reset link to Kafka off the request's transaction, the same shape
 * as {@link VerificationEmailListener} -- see that class for why the send must
 * not sit inside one.
 *
 * <p>
 * {@code fallbackExecution} because forgotPassword is a read followed by a mail
 * send and has no transaction of its own.
 *
 * <p>
 * Failures are logged rather than rethrown. The response to "forgot password"
 * is deliberately the same whether or not an account exists, so it must not
 * start differing because a send failed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetEmailListener {

	private final KafkaEventPublisher publisher;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
		try {
			publisher.publishEmail(EmailKind.PASSWORD_RESET, event.getUserId(), MDC.get("traceId"));
		} catch (Exception e) {
			log.error("Failed to queue a password reset link for user {}", event.getUserId(), e);
		}
	}
}
