package com.wtsend.backend.socket;

import java.util.List;

import org.springframework.stereotype.Service;

import com.corundumstudio.socketio.SocketIOClient;
import com.wtsend.backend.common.exception.AppException;
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
	private final RedisPresenceStore onlineUsers;
	private final ConversationService conversationService;
	private final SocketRelay relay;

	public void onClientConnected(Object authToken, SocketIOClient client) {
		if (authToken == null) {
			client.disconnect();
			return;
		}
		String userId = authToken.toString();
		UserResponse user;
		try {
			// findById throws for an unknown id, it never returns null -- unguarded,
			// a deleted user threw straight out of netty's connect listener. Catching
			// AppException rather than RuntimeException so a genuine mapping failure
			// still surfaces instead of being reported as "no such user".
			user = userService.findById(userId);
		} catch (AppException e) {
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
			broadcastPresence(user.getId(), true);
	}

	public void onClientDisconnected(SocketIOClient client) {
		UserResponse user = client.get("user");
		if (user == null)
			return;

		// Only the last socket closing takes the user offline; a second tab must
		// not look like a disconnect.
		if (onlineUsers.remove(user.getId(), client.getSessionId()))
			broadcastPresence(user.getId(), false);

		log.info("{} disconnected.", user.getUsername());
	}

	/**
	 * Through the relay, so a client on another instance hears about it too --
	 * this used to go to the arriving client's namespace, which is this process
	 * only.
	 */
	private void broadcastPresence(String userId, boolean online) {
		relay.toAll("presence-changed", new PresenceChange(userId, online));
	}

	public void subscribeUserToConversations(SocketIOClient client) {
		UserResponse user = client.get("user");
		if (user == null)
			return;

		// The personal room first, and unconditionally. It used to be joined
		// after the loop below, behind an early return for "this user has no
		// conversations yet" -- so a brand new account joined no room at all,
		// and since every message is addressed to the participants' personal
		// rooms, nothing could ever be delivered to it. The first conversation
		// of a user's life was the one case that never worked.
		client.joinRoom(SocketRooms.user(user.getId()));

		List<Long> conversationIds = conversationService.getConversationIdsByUserId(user.getId());
		if (conversationIds == null || conversationIds.isEmpty())
			return;

		conversationIds.forEach(ci -> {
			client.joinRoom(SocketRooms.conversation(ci));
			log.info("{} joined to room {} ", user.getUsername(), ci);
		});
	}

}
