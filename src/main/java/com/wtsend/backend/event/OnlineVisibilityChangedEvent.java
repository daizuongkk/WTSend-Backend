package com.wtsend.backend.event;

import lombok.Getter;

/**
 * A user turned "show my online status" on or off.
 *
 * <p>
 * Published by the update that changed the column and handled after commit, so
 * nothing is announced to clients that the database has not accepted yet --
 * the same discipline as the message and conversation events.
 */
@Getter
public class OnlineVisibilityChangedEvent {

	private final String userId;
	private final boolean visible;

	public OnlineVisibilityChangedEvent(String userId, boolean visible) {
		this.userId = userId;
		this.visible = visible;
	}
}
