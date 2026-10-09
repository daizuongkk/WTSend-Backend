package com.wtsend.backend.listener;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.wtsend.backend.event.VerificationEmailRequestedEvent;
import com.wtsend.backend.messaging.KafkaEventPublisher;
import com.wtsend.backend.messaging.event.EmailRequestedEvent.EmailKind;

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
 * What it does now is hand the request to Kafka rather than call Resend. The
 * difference that buys: a send that fails is retried and then kept in
 * {@code wtsend.notify.email.v1.dlt}, where it can be looked at and replayed.
 * Before, a failed send was a log line and nothing else.
 *
 * <p>
 * The cooldown is no longer a problem to work around here either --
 * {@code VERIFY_EMAIL_COOLDOWN} is raised in the consumer, which treats it as
 * success. It used to escape through {@code signIn} and fail the login of an
 * unverified user who simply tried twice.
 *
 * <p>
 * Failures are logged, not rethrown: after commit there is nothing left to roll
 * back. An account that never receives its link is not stranded -- signing in
 * requests another, and an unverified account is deleted after 24 hours.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VerificationEmailListener {

	private final KafkaEventPublisher publisher;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onVerificationEmailRequested(VerificationEmailRequestedEvent event) {
		try {
			// No user lookup here: the event carries an id and the consumer reads
			// the row. Keeping the address off the topic matters -- records live
			// there for seven days.
			publisher.publishEmail(EmailKind.VERIFY_LINK, event.getUserId(), MDC.get("traceId"));
		} catch (Exception e) {
			log.error("Failed to queue a verification link for user {}", event.getUserId(), e);
		}
	}
}
