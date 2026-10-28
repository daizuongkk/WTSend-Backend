package com.wtsend.backend.event;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * "This user asked to reset their password."
 *
 * <p>
 * Carries only the id, like {@link VerificationEmailRequestedEvent}: the
 * listener runs after commit with no session, and a durable topic must never
 * carry the token itself.
 */
@Getter
@AllArgsConstructor
public class PasswordResetRequestedEvent {

  private final UUID userId;

}
