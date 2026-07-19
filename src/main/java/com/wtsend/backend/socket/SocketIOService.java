package com.wtsend.backend.socket;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;

import com.corundumstudio.socketio.HandshakeData;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.services.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SocketIOService {

	private final UserSessionService userSession;
	private final JwtDecoder jwtDecoder;
	private final UserService userService;

	@OnConnect
	public void onConnect(SocketIOClient client) {
		HandshakeData handshakeData = client.getHandshakeData();
		String token = handshakeData.getSingleUrlParam("token");
		if (token == null || token.isEmpty()) {
			log.warn("No token provided from: {}",
					handshakeData.getAddress());
			client.disconnect();
			return;
		}

		try {
			Jwt jwt = jwtDecoder.decode(token);

			String userId = jwt.getSubject();

			UserResponse user = userService.findById(userId);

			if (user == null) {
				client.disconnect();
				return;
			}

			handshakeData.setAuthToken(userId);
			log.info("Authorized: {}", jwt.getSubject());

		} catch (Exception e) {
			log.error("Invalid token from: {}",
					handshakeData.getAddress());
			client.disconnect();
			return;
		}

		Object authToken = handshakeData.getAuthToken();

		userSession.onClientConnected(authToken, client);
		userSession.subscribeUserToConversations(client);
	}

	@OnDisconnect
	public void onDisconnect(SocketIOClient client) {

		userSession.onClientDisconnected(client);

	}

	@OnEvent("join-conversation")
	public void onJoinConversation(SocketIOClient client, String convoId) {
		client.joinRoom(convoId);
		log.info("{} joined to room {} ", client.getSessionId(), convoId);

	}

}
