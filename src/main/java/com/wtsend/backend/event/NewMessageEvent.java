package com.wtsend.backend.event;

import com.wtsend.backend.model.Conversation;
import com.wtsend.backend.model.Message;

import lombok.Getter;

@Getter
public class NewMessageEvent {
	private final Long conversationId;
	private final Conversation conversation;
	private final Message message;

	public NewMessageEvent(Conversation conversation, Message message) {
		this.conversation = conversation;
		this.conversationId = conversation.getId();
		this.message = message;
	}
}