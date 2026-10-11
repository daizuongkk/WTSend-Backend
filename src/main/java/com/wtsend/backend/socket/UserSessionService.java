package com.wtsend.backend.socket;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
	private final PresenceAnnouncer announcer;

	/**
	 * Who may see this socket's user come and go, cached on the socket at
	 * connect.
	 *
	 * <p>
	 * Cached because the alternative is a two-EXISTS query on every presence
	 * change, including the ones a page refresh causes. The cost of caching is
	 * that a friendship made during the session is not reflected until the
	 * socket reconnects: the new friend sees them online on their next page
	 * load, which is the same moment the friendship itself appears in their UI.
	 */
	private static final String AUDIENCE = "presenceAudience";

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

		boolean cameOnline = onlineUsers.add(user.getId(), client.getSessionId(),
				user.isShowOnlineStatus());
		log.info("{} connected with socket {}", user.getUsername(), client.getSessionId());

		// The snapshot goes to the arriving client alone. It used to be broadcast
		// to everyone on every connect, so one person opening a tab resent the
		// whole online list to every socket.
		// Only the people entitled to see it, not the whole online list. It used
		// to be every online user id, so a new account learned who else was using
		// the app before it had a single friend.
		List<String> audience = announcer.audienceFor(user.getId());
		client.set(AUDIENCE, audience);

		Set<String> visible = new LinkedHashSet<>(audience);
		visible.retainAll(onlineUsers.onlineUserIds());
		client.sendEvent("online-users", visible);

		// A user who has turned their status off is not announced, and was never
		// put in the online list to begin with.
		if (cameOnline && user.isShowOnlineStatus())
			announcer.announce(user.getId(), audience, true);
	}

	/**
	 * A socket is NOT closed when the access token that opened it expires, and
	 * that is deliberate.
	 *
	 * <p>
	 * It was, briefly. The access token lives five minutes, so every socket in
	 * the system was torn down and rebuilt every five minutes -- and each
	 * teardown is a real disconnect, which drops the user from the online list
	 * and announces them offline to every friend. Everyone blinked offline on a
	 * timer, and every realtime frame in the gap was lost, because the server
	 * does not replay. The protection it bought was thin by comparison: the
	 * socket carries no authority of its own, every write still goes through the
	 * REST API, and that API does check the token on every request.
	 */
	public void onClientDisconnected(SocketIOClient client) {
		UserResponse user = client.get("user");
		if (user == null)
			return;

		// Only the last socket closing takes the user offline; a second tab must
		// not look like a disconnect.
		// Not gated on showOnlineStatus, deliberately. The UserResponse cached on
		// this socket is from connect time, so it can be stale if the user has
		// toggled since -- and telling clients that an already-invisible user is
		// offline changes nothing for them, while skipping it wrongly would leave
		// someone shown online after they left.
		if (onlineUsers.remove(user.getId(), client.getSessionId()))
			announcer.announce(user.getId(), audienceOf(client), false);

		log.info("{} disconnected.", user.getUsername());
	}

	/**
	 * The audience cached at connect, or a fresh lookup if this socket somehow
	 * has none -- better one query than a departure nobody is told about.
	 */
	private List<String> audienceOf(SocketIOClient client) {
		List<String> cached = client.get(AUDIENCE);
		if (cached != null)
			return cached;

		UserResponse user = client.get("user");
		return user == null ? List.of() : announcer.audienceFor(user.getId());
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
