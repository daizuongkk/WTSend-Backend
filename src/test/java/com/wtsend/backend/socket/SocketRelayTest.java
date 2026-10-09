package com.wtsend.backend.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.corundumstudio.socketio.BroadcastOperations;
import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The relay's two jobs: publish what it emits, and ignore what it published.
 */
class SocketRelayTest {

	private static final String ROOM = "conversation:7";

	private SocketIOServer server;
	private BroadcastOperations roomOperations;
	private StringRedisTemplate redis;
	private SocketRelay relay;

	@BeforeEach
	void setUp() {
		server = Mockito.mock(SocketIOServer.class);
		roomOperations = Mockito.mock(BroadcastOperations.class);
		redis = Mockito.mock(StringRedisTemplate.class);
		when(server.getRoomOperations(any())).thenReturn(roomOperations);
		when(server.getBroadcastOperations()).thenReturn(roomOperations);
		relay = new SocketRelay(server, redis, new InstanceId("node-a"));
	}

	@Test
	void anEmissionGoesToLocalClientsAndOntoTheChannel() {
		relay.toRoom(ROOM, "new-message", Map.of("content", "xin chào"));

		verify(roomOperations).sendEvent(eq("new-message"), any(Object.class));
		verify(redis).convertAndSend(eq(SocketRelay.CHANNEL), any(String.class));
	}

	@Test
	void anEventFromAnotherInstanceIsEmittedToThisOnesClients() {
		relay.onMessage(envelopeFrom("node-b", ROOM, "new-message"), null);

		verify(server).getRoomOperations(ROOM);
		verify(roomOperations).sendEvent(eq("new-message"), any(Object.class));
	}

	@Test
	void ourOwnEventComingBackIsNotEmittedTwice() {
		// Every instance subscribes to the channel it publishes on, this one
		// included. Without the guard the sender's own clients get each message
		// twice -- once locally, once off the channel.
		relay.onMessage(envelopeFrom("node-a", ROOM, "new-message"), null);

		verify(roomOperations, never()).sendEvent(any(), any(Object.class));
	}

	@Test
	void aRelayedPayloadKeepsItsTimestampsAsStrings() throws Exception {
		// The relay and netty-socketio serialise the same payloads with two
		// different mappers. If only one of them knew about JavaTimeModule, a
		// client on another instance would get createdAt as an array of numbers
		// and nothing local would ever reveal it.
		Instant createdAt = Instant.parse("2026-10-09T15:30:00Z");
		relay.toRoom(ROOM, "new-message", Map.of("createdAt", createdAt));

		ArgumentCaptor<String> published = ArgumentCaptor.forClass(String.class);
		verify(redis).convertAndSend(eq(SocketRelay.CHANNEL), published.capture());

		assertThat(new ObjectMapper().readTree(published.getValue())
				.path("payload").path("createdAt").asText())
				.isEqualTo("2026-10-09T15:30:00Z");
	}

	private DefaultMessage envelopeFrom(String instance, String room, String event) {
		String body = """
				{"from":"%s","rooms":["%s"],"event":"%s","payload":{"content":"xin chào"}}
				""".formatted(instance, room, event);
		return new DefaultMessage(SocketRelay.CHANNEL.getBytes(StandardCharsets.UTF_8),
				body.getBytes(StandardCharsets.UTF_8));
	}
}
