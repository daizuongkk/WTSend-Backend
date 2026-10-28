package com.wtsend.backend.event;

import java.util.UUID;

import lombok.Getter;

/**
 * "This user needs a verification link."
 *
 * <p>
 * Carries only the id, not the entity: the listener runs after the transaction
 * has committed, with no open session, so it loads the user itself. That is
 * also the shape the Kafka event will take when this listener becomes a
 * producer -- a durable topic must not carry an entity, and it must not carry
 * the token either.
 */
@Getter
public class VerificationEmailRequestedEvent {

  private final UUID userId;

  public VerificationEmailRequestedEvent(UUID userId) {
    this.userId = userId;
  }
}
