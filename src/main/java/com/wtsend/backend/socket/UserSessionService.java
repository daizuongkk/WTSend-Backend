package com.wtsend.backend.socket;

import java.util.List;

import org.springframework.stereotype.Service;

import com.corundumstudio.socketio.SocketIOClient;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.services.ConversationService;
import com.wtsend.backend.services.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSessionService {
	private final UserService userService;
	private final OnlineUserStore onlineUsers;
	private final ConversationService conversationService;

	public void onClientConnected(Object authToken, SocketIOClient client) {
		if (authToken == null) {
			client.disconnect();
			return;
		}
		String userId = authToken.toString();
		UserResponse user = userService.findById(userId);
		if (user == null) {
			log.warn("Auth cache miss for socket {}", client.getSessionId());
			client.disconnect();
			return;
		}
		client.set("user", user);
		onlineUsers.add(user.getId(), client.getSessionId());
		log.info("{} connected with socket {}", user.getUsername(), client.getSessionId());
		client.getNamespace()
				.getBroadcastOperations()
				.sendEvent("online-users", onlineUsers.getStore().keySet());
	}

	public void onClientDisconnected(SocketIOClient client) {
		UserResponse user = client.get("user");
		if (user == null)
			return;

		onlineUsers.remove(user.getId(), client.getSessionId());
		client.getNamespace()
				.getBroadcastOperations()
				.sendEvent("online-users", onlineUsers.getStore().keySet());
		log.info("{} disconnected.", user.getUsername());
	}

	public void subscribeUserToConversations(SocketIOClient client) {
		UserResponse user = client.get("user");
		if (user == null)
			return;
		List<Long> conversationIds = conversationService.getConversationIdsByUserId(user.getId());
		if (conversationIds == null || conversationIds.isEmpty())
			return;

		conversationIds.forEach(ci -> {
			client.joinRoom(ci.toString());
			log.info("{} joined to room {} ", user.getUsername(), ci);
		});

		client.joinRoom(user.getId());
	}

}
