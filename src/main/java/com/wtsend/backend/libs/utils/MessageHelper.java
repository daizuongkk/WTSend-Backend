package com.wtsend.backend.libs.utils;

import java.util.Map;

import com.corundumstudio.socketio.SocketIOServer;
import com.wtsend.backend.dto.response.ConversationResponse;
import com.wtsend.backend.dto.response.MessageResponse;
import com.wtsend.backend.exceptions.ForbiddenException;
import com.wtsend.backend.model.Conversation;
import com.wtsend.backend.model.Message;
import com.wtsend.backend.model.User;

public class MessageHelper {
	private MessageHelper() {
	}

	public static void updateConversationAfterCreateMessage(Conversation conversation, Message message, String senderId) {
		conversation.setLastMessageAt(message.getCreatedAt());

		conversation.setLastMessage(message);

		conversation.getParticipants().forEach(p -> {
			String memberId = p.getUser().getId();
			boolean isSender = memberId.equals(senderId);
			Long preCount = p.getUnreadCounts() == null ? 0 : p.getUnreadCounts();
			if (isSender) {
				p.setUnreadCounts(0L);
				// p.setLastSeenMessage(message);
			} else
				p.setUnreadCounts(preCount + 1);
		});
	}

	public static void checkMembership(Conversation conversation, User user) {

		if (conversation.getParticipants().stream().noneMatch(p -> p.getUser().getId().equals(user.getId())))
			throw new ForbiddenException("User does not have permission to perform this action, with id: " + user.getId());

	}

	public static void emitNewMessage(SocketIOServer server, ConversationResponse conversation, MessageResponse message) {

		server.getRoomOperations(conversation.getId().toString()).sendEvent("new-message",
				Map.of("message", message, "conversation", conversation));

	}
}
