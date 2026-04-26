package com.wtsend.backend.socket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.SpringAnnotationScanner;
import com.corundumstudio.socketio.protocol.JacksonJsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wtsend.backend.dtos.response.UserResponse;
import com.wtsend.backend.services.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class SocketIOConfig {

	private final SocketIOProperties properties;

	private final JwtDecoder jwtDecoder;

	private final UserService userService;

	@Bean
	public SocketIOServer socketIOServer() {
		com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
		config.setJsonSupport(new JacksonJsonSupport() {
			@Override
			protected void init(ObjectMapper objectMapper) {
				super.init(objectMapper);
				objectMapper.registerModule(new JavaTimeModule());
				objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
			}
		});
		config.setHostname(properties.getHost());
		config.setPort(properties.getPort());
		config.setOrigin(properties.getOrigin());
		config.setBossThreads(properties.getBossCount());
		config.setWorkerThreads(properties.getWorkerCount());
		config.setAllowCustomRequests(properties.getAllowCustomRequests());
		config.setUpgradeTimeout(properties.getUpgradeTimeout());
		config.setPingTimeout(properties.getPingTimeout());
		config.setPingInterval(properties.getPingInterval());
		config.setMaxFramePayloadLength(properties.getMaxFramePayloadLength());
		config.setMaxHttpContentLength(properties.getMaxHttpContentLength());
		config.setAuthorizationListener(handshakeData -> {
			String token = handshakeData.getSingleUrlParam("token");
			if (token == null || token.isEmpty()) {
				log.warn("No token provided from: {}",
						handshakeData.getAddress());
				return AuthorizationResult.FAILED_AUTHORIZATION;
			}

			try {
				Jwt jwt = jwtDecoder.decode(token);

				String userId = jwt.getSubject();

				UserResponse user = userService.findById(userId);

				if (user == null)
					return AuthorizationResult.FAILED_AUTHORIZATION;

				handshakeData.setAuthToken(userId);
				log.info("Authorized: {}", jwt.getSubject());
				return AuthorizationResult.SUCCESSFUL_AUTHORIZATION;

			} catch (Exception e) {
				log.error("Invalid token from: {}",
						handshakeData.getAddress());
				return AuthorizationResult.FAILED_AUTHORIZATION;
			}
		});

		return new SocketIOServer(config);
	}

	@Bean
	public SpringAnnotationScanner springAnnotationScanner(SocketIOServer server) {
		return new SpringAnnotationScanner(server);
	}
}
