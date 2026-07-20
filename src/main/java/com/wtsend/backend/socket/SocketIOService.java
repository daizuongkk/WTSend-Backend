package com.wtsend.backend.socket;

import org.springframework.stereotype.Service;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.repository.ParticipantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SocketIOService {

	private final UserSessionService userSession;
	private final ParticipantRepository participantRepo;

	@OnConnect
	public void onConnect(SocketIOClient client) {
		// The handshake already passed SocketIOConfig's AuthorizationListener, which
		// decoded and verified the token -- re-decoding here just cost a second JWT
		// verify and DB round trip per connection.
		Object authToken = client.getHandshakeData().getAuthToken();

		userSession.onClientConnected(authToken, client);
		userSession.subscribeUserToConversations(client);
	}

	@OnDisconnect
	public void onDisconnect(SocketIOClient client) {

		userSession.onClientDisconnected(client);

	}

	@OnEvent("join-conversation")
	public void onJoinConversation(SocketIOClient client, String convoId) {
		UserResponse user = client.get("user");
		if (user == null) {
			client.disconnect();
			return;
		}

		Long conversationId;
		try {
			conversationId = Long.valueOf(convoId);
		} catch (NumberFormatException e) {
			log.warn("{} sent a malformed conversation id: {}", user.getId(), convoId);
			return;
		}

		// Without this the room id is simply trusted, so any socket could subscribe
		// to any conversation's traffic.
		if (participantRepo.findByConversationIdAndUserId(conversationId, user.getId()).isEmpty()) {
			log.warn("{} tried to join conversation {} without membership", user.getId(), conversationId);
			return;
		}

		client.joinRoom(SocketRooms.conversation(conversationId));
		log.info("{} joined to room {} ", client.getSessionId(), conversationId);

	}

}
