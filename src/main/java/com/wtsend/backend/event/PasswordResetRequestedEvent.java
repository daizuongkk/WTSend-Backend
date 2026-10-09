package com.wtsend.backend.event;

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
public class PasswordResetRequestedEvent {

	private final String userId;

	public PasswordResetRequestedEvent(String userId) {
		this.userId = userId;
	}
}
