package com.wtsend.backend.event;

import java.util.List;

import com.wtsend.backend.dto.response.ConversationResponse;

import lombok.Getter;

/**
 * Carries a fully mapped DTO rather than the entity, for the same reason as
 * {@link NewMessageEvent}: the listener runs AFTER_COMMIT with no open session,
 * so a lazy association would blow up there. Mapping happens inside the
 * transaction instead -- and once, rather than once per recipient.
 */
@Getter
public class ConversationCreatedEvent {

	private final ConversationResponse conversation;

	/**
	 * Every participant, the creator included. Their other tabs and devices have
	 * no other way to learn the conversation exists; only the tab that issued
	 * the request knows, because it adds it locally.
	 */
	private final List<String> recipientUserIds;

	public ConversationCreatedEvent(ConversationResponse conversation, List<String> recipientUserIds) {
		this.conversation = conversation;
		this.recipientUserIds = List.copyOf(recipientUserIds);
	}
}
