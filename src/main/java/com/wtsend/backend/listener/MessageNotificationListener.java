package com.wtsend.backend.listener;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.corundumstudio.socketio.SocketIOServer;
import com.wtsend.backend.event.NewMessageEvent;
import com.wtsend.backend.socket.SocketRooms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageNotificationListener {
	private final SocketIOServer server;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onNewMessage(NewMessageEvent event) {
		try {
			server.getRoomOperations(SocketRooms.conversation(event.getConversationId()))
					.sendEvent("new-message", Map.of(
							"message", event.getMessage(),
							"conversation", event.getConversation()));
		} catch (Exception e) {
			// The transaction is already committed; an exception here would be
			// swallowed by Spring and the message would vanish without a trace.
			log.error("Failed to broadcast new-message for conversation {}", event.getConversationId(), e);
		}
	}
}
