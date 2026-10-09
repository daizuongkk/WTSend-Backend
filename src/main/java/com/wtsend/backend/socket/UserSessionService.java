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
		UserResponse user;
		try {
			// findById throws for an unknown id, it never returns null -- unguarded,
			// a deleted user threw straight out of netty's connect listener.
			user = userService.findById(userId);
		} catch (RuntimeException e) {
			log.warn("Rejecting socket {}: no user for id {}", client.getSessionId(), userId);
			client.disconnect();
			return;
		}
		client.set("user", user);
		boolean cameOnline = onlineUsers.add(user.getId(), client.getSessionId());
		log.info("{} connected with socket {}", user.getUsername(), client.getSessionId());

		// The snapshot goes to the arriving client alone. It used to be broadcast
		// to everyone on every connect, so one person opening a tab resent the
		// whole online list to every socket.
		client.sendEvent("online-users", onlineUsers.onlineUserIds());

		if (cameOnline)
			broadcastPresence(client, user.getId(), true);
	}

	public void onClientDisconnected(SocketIOClient client) {
		UserResponse user = client.get("user");
		if (user == null)
			return;

		// Only the last socket closing takes the user offline; a second tab must
		// not look like a disconnect.
		if (onlineUsers.remove(user.getId(), client.getSessionId()))
			broadcastPresence(client, user.getId(), false);

		log.info("{} disconnected.", user.getUsername());
	}

	private void broadcastPresence(SocketIOClient client, String userId, boolean online) {
		client.getNamespace()
				.getBroadcastOperations()
				.sendEvent("presence-changed", new PresenceChange(userId, online));
	}

	public void subscribeUserToConversations(SocketIOClient client) {
		UserResponse user = client.get("user");
		if (user == null)
			return;
		List<Long> conversationIds = conversationService.getConversationIdsByUserId(user.getId());
		if (conversationIds == null || conversationIds.isEmpty())
			return;

		conversationIds.forEach(ci -> {
			client.joinRoom(SocketRooms.conversation(ci));
			log.info("{} joined to room {} ", user.getUsername(), ci);
		});

		client.joinRoom(SocketRooms.user(user.getId()));
	}

}
