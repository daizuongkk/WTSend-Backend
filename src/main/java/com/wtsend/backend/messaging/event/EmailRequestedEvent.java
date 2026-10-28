package com.wtsend.backend.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * "Send this user one of our emails." The only thing on the wire.
 *
 * <p>
 * It deliberately carries no token, no address and no rendered body.
 *
 * <ul>
 * <li><b>No token.</b> The topic keeps records for seven days; a live
 * verification token sitting in it is a credential at rest. The consumer
 * creates the token itself, exactly as the synchronous path did.
 * <li><b>No email address.</b> It would be a copy of a column the consumer is
 * about to read anyway, and it would put an address in a log that outlives the
 * request.
 * <li><b>No rendered HTML.</b> Fixing a typo in a template would otherwise
 * mean replaying the topic.
 * </ul>
 *
 * <p>
 * So: which user, and which letter. The consumer reads the rest.
 *
 * @param eventId    for tracing a single request through to the dead-letter
 *                   topic
 * @param occurredAt when the request was made, not when it is handled
 * @param version    payload version within this topic, for the day a field is
 *                   added
 * @param traceId    the MDC trace id of the request that caused this, so the
 *                   send can be lined up with the signup that asked for it
 */
public record EmailRequestedEvent(
    UUID eventId,
    Instant occurredAt,
    int version,
    String traceId,
    EmailKind kind,
    UUID userId) {

  public static final int CURRENT_VERSION = 1;

  public enum EmailKind {
    VERIFY_LINK,
    PASSWORD_RESET
  }

  public static EmailRequestedEvent of(EmailKind kind, UUID userId, String traceId) {
    return new EmailRequestedEvent(UUID.randomUUID(), Instant.now(),
        CURRENT_VERSION, traceId, kind, userId);
  }
}
