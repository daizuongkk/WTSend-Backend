package com.wtsend.backend.listener;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.wtsend.backend.event.ConversationCreatedEvent;
import com.wtsend.backend.event.ConversationReadEvent;
import com.wtsend.backend.socket.SocketRelay;
import com.wtsend.backend.socket.SocketRooms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Conversation-level broadcasts, moved off ConversationService so that no
 * service holds a SocketIOServer and every emission happens after the write is
 * committed -- the discipline {@link MessageNotificationListener} already
 * followed for new messages.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConversationNotificationListener {

	private final SocketRelay relay;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onConversationCreated(ConversationCreatedEvent event) {
		try {
			relay.toRooms(event.getRecipientUserIds().stream().map(SocketRooms::user).toList(),
					"new-group", event.getConversation());
		} catch (Exception e) {
			// Already committed, so there is nothing to roll back and Spring would
			// swallow this silently.
			log.error("Failed to broadcast new-group for conversation {}", event.getConversation().getId(), e);
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onConversationRead(ConversationReadEvent event) {
		try {
			relay.toConversation(event.getConversation(), "read-message", Map.of(
					"conversation", event.getConversation(),
					"lastMessage", event.getLastMessage()));
		} catch (Exception e) {
			log.error("Failed to broadcast read-message for conversation {}", event.getConversationId(), e);
		}
	}
}
