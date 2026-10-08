package com.wtsend.backend.event;

import com.wtsend.backend.dto.response.ConversationResponse;
import com.wtsend.backend.dto.response.LastMessage;

import lombok.Getter;

/**
 * A read receipt, broadcast to the conversation room after the participant's
 * row has actually committed.
 *
 * <p>
 * It used to be sent from inside the open transaction, so clients could be told
 * a message had been read before -- or without -- the write becoming durable.
 */
@Getter
public class ConversationReadEvent {

	private final Long conversationId;
	private final ConversationResponse conversation;
	private final LastMessage lastMessage;

	public ConversationReadEvent(ConversationResponse conversation, LastMessage lastMessage) {
		this.conversation = conversation;
		this.conversationId = conversation.getId();
		this.lastMessage = lastMessage;
	}
}
