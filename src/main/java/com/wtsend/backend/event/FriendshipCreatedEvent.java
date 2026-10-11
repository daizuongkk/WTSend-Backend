package com.wtsend.backend.event;

import java.util.List;

import lombok.Getter;

/**
 * Two accounts just became friends.
 *
 * <p>
 * Carries ids only. Nothing downstream needs the entities, and the listener
 * runs AFTER_COMMIT with no open session -- the same reason
 * {@link ConversationCreatedEvent} carries a mapped DTO rather than a
 * Conversation.
 */
@Getter
public class FriendshipCreatedEvent {

	private final List<String> userIds;

	public FriendshipCreatedEvent(String oneUserId, String otherUserId) {
		this.userIds = List.of(oneUserId, otherUserId);
	}
}
