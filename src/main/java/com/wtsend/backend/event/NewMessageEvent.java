package com.wtsend.backend.event;

import com.wtsend.backend.dto.response.ConversationResponse;
import com.wtsend.backend.dto.response.MessageResponse;

import lombok.Getter;

/**
 * Carries fully-mapped DTOs rather than JPA entities: the listener runs
 * AFTER_COMMIT with no open session, so a lazy association on an entity would
 * blow up there. Mapping happens inside the transaction instead.
 */
@Getter
public class NewMessageEvent {
	private final Long conversationId;
	private final ConversationResponse conversation;
	private final MessageResponse message;

	public NewMessageEvent(ConversationResponse conversation, MessageResponse message) {
		this.conversation = conversation;
		this.conversationId = conversation.getId();
		this.message = message;
	}
}
