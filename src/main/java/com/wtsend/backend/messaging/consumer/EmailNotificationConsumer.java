package com.wtsend.backend.messaging.consumer;

import java.util.Set;

import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.messaging.event.EmailRequestedEvent;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends the email the event asked for, off the request thread.
 *
 * <p>
 * This is the whole point of the Kafka hop. The send used to happen in an
 * {@code AFTER_COMMIT} listener on the request thread, which kept the HTTP
 * request waiting on a third-party call and -- worse -- left nothing behind
 * when that call failed. Now a failure is retried and then kept in
 * {@code .dlt}, where it can be read and replayed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationConsumer {

  /**
   * Deterministic failures: the same record will fail the same way for ever,
   * so they go to the dead-letter topic without burning five attempts first.
   *
   * <p>
   * Pointedly a set of codes rather than {@code AppException}. That one type
   * also covers {@link ErrorCode#EMAIL_SEND_FAILED}, which wraps a 502/503
   * from Resend and is exactly the case retrying exists for -- treating the
   * whole class as non-retryable would send every provider blip straight to
   * the DLT.
   */
  private static final Set<ErrorCode> PERMANENT = Set.of(
      ErrorCode.USER_NOT_FOUND,
      ErrorCode.EMAIL_ALREADY_EXISTS);

  /** Refusals that mean the mail is already in hand, so the record is done. */
  private static final Set<ErrorCode> COOLDOWNS = Set.of(
      ErrorCode.VERIFY_EMAIL_COOLDOWN,
      ErrorCode.PASSWORD_RESET_COOLDOWN);

  private final UserRepository userRepository;
  private final EmailService emailService;

  @KafkaListener(topics = "${app.messaging.email-notifications.name}", groupId = "${spring.kafka.consumer.group-id}")
  public void onEmailRequested(EmailRequestedEvent event) {
    // Puts the id of the request that caused this into the consumer's log
    // lines, so a send can be lined up with the signup that asked for it
    // even though they are on different threads minutes apart.
    MDC.put("traceId", event.traceId() == null ? event.eventId().toString() : event.traceId());
    try {
      User user = userRepository.findById(event.userId())
          .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)
              .withDetail("id=" + event.userId()));

      switch (event.kind()) {
        case VERIFY_LINK -> emailService.sendVerifyLink(user);
        case PASSWORD_RESET -> emailService.sendPasswordResetLink(user);
      }

      log.info("Sent {} for user {} (eventId={})", event.kind(), event.userId(), event.eventId());
    } catch (AppException e) {
      handle(event, e);
    } finally {
      MDC.remove("traceId");
    }
  }

  private void handle(EmailRequestedEvent event, AppException e) {
    ErrorCode code = e.getErrorCode();

    // The cooldown already in EmailService is the deduplication, reused rather
    // than rebuilt: it means "do not spam this user", which also covers two
    // different events asking for the same mail. Hitting it is the system
    // working, so the record is done.
    //
    // Both kinds, not just the verification one. PASSWORD_RESET_COOLDOWN used
    // to fall through to the retries and then to the DLT, where a replay would
    // have sent a second reset link and retired the live one the user was
    // probably about to click.
    if (COOLDOWNS.contains(code)) {
      log.info("Skipping {} for user {}: still inside the send cooldown",
          event.kind(), event.userId());
      return;
    }

    if (PERMANENT.contains(code))
      throw new NonRetryableEventException(
          "%s is permanent for eventId=%s".formatted(code, event.eventId()), e);

    // Everything else -- a provider timeout, a 503 -- is worth another go.
    throw e;
  }
}
