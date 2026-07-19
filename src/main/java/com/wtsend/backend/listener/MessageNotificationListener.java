package com.wtsend.backend.listener;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.corundumstudio.socketio.SocketIOServer;
import com.wtsend.backend.event.NewMessageEvent;
import com.wtsend.backend.libs.ConversationMapper;
import com.wtsend.backend.libs.MessageMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MessageNotificationListener {
	private final SocketIOServer server;
	private final ConversationMapper conversationMapper;
	private final MessageMapper messageMapper;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onNewMessage(NewMessageEvent event) {
		server.getRoomOperations(event.getConversationId().toString())
				.sendEvent("new-message", Map.of(
						"message", messageMapper.toResponse(event.getMessage()),
						"conversation", conversationMapper.toResponse(event.getConversation())));
	}
}