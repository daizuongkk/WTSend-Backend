package com.wtsend.backend.socket;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.corundumstudio.socketio.BroadcastOperations;
import com.corundumstudio.socketio.SocketIOServer;

/**
 * Proves the relay actually crosses a process boundary, which
 * {@link SocketRelayTest} cannot: it mocks the Redis template, so it verifies
 * that something was published, not that anything arrives.
 *
 * <p>
 * The second instance is a second SocketRelay with its own instance id and its
 * own mocked server, subscribed to the same channel on the same Redis. That is
 * the whole of what differs between two processes here -- and it exercises the
 * part that fails without a word if it is wrong: the listener container, the
 * topic name, and the envelope surviving a serialise/parse round trip.
 */
@SpringBootTest
class SocketRelayChannelTest {

	/** Keeps the real server off port 9092 while this context is up. */
	@MockitoBean
	private SocketIOServer server;

	@Autowired
	private SocketRelay thisInstance;
	@Autowired
	private StringRedisTemplate redis;
	/**
	 * By name: Spring Data's repository support registers a container of its own
	 * for keyspace notifications, so the type alone is ambiguous.
	 */
	@Autowired
	@Qualifier("socketRelayContainer")
	private RedisMessageListenerContainer container;

	private SocketIOServer otherServer;
	private BroadcastOperations otherRoomOperations;
	private SocketRelay otherInstance;

	@BeforeEach
	void subscribeASecondInstance() {
		// This instance emits locally before publishing, so its server needs a
		// room to emit into even though the assertions are all about the other one.
		BroadcastOperations localOperations = Mockito.mock(BroadcastOperations.class);
		when(server.getRoomOperations(any())).thenReturn(localOperations);
		when(server.getBroadcastOperations()).thenReturn(localOperations);

		otherServer = Mockito.mock(SocketIOServer.class);
		otherRoomOperations = Mockito.mock(BroadcastOperations.class);
		when(otherServer.getRoomOperations(any())).thenReturn(otherRoomOperations);
		when(otherServer.getBroadcastOperations()).thenReturn(otherRoomOperations);

		otherInstance = new SocketRelay(otherServer, redis, new InstanceId("node-b"));
		container.addMessageListener(otherInstance, new ChannelTopic(SocketRelay.CHANNEL));
	}

	@AfterEach
	void unsubscribeIt() {
		container.removeMessageListener(otherInstance);
	}

	@Test
	void aMessageSentOnThisInstanceReachesTheOther() {
		thisInstance.toRoom(SocketRooms.conversation(42L), "new-message",
				Map.of("message", Map.of("content", "gửi từ node khác")));

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			verify(otherServer).getRoomOperations(SocketRooms.conversation(42L));
			verify(otherRoomOperations).sendEvent(eq("new-message"), any(Object.class));
		});
	}

	@Test
	void aBroadcastReachesEveryInstance() {
		thisInstance.toAll("presence-changed", new PresenceChange("user-1", true));

		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			verify(otherServer).getBroadcastOperations();
			verify(otherRoomOperations).sendEvent(eq("presence-changed"), any(Object.class));
		});
	}
}
