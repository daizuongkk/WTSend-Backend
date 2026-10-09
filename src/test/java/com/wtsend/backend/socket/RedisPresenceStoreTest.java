package com.wtsend.backend.socket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.corundumstudio.socketio.SocketIOServer;

/**
 * The presence transitions, against a real Redis -- the whole point of the Lua
 * is what the server does with concurrent writes, which a mock would not
 * exercise.
 */
@SpringBootTest
class RedisPresenceStoreTest {

	/** Keeps the real server off port 9092 while this context is up. */
	@MockitoBean
	private SocketIOServer server;

	@Autowired
	private RedisPresenceStore presence;
	@Autowired
	private StringRedisTemplate redis;

	private final String userId = "presence-test-" + UUID.randomUUID();

	@AfterEach
	void removeWhatThisTestWrote() {
		redis.delete("presence:sockets:" + userId);
		redis.opsForZSet().remove("presence:online", userId);
	}

	@Test
	void theFirstSocketBringsTheUserOnlineAndTheLastTakesThemOffline() {
		UUID socket = UUID.randomUUID();

		assertThat(presence.add(userId, socket)).isTrue();
		assertThat(presence.onlineUserIds()).contains(userId);

		assertThat(presence.remove(userId, socket)).isTrue();
		assertThat(presence.onlineUserIds()).doesNotContain(userId);
	}

	@Test
	void aSecondTabIsNotAnArrivalAndClosingItIsNotADeparture() {
		UUID firstTab = UUID.randomUUID();
		UUID secondTab = UUID.randomUUID();

		assertThat(presence.add(userId, firstTab)).isTrue();
		assertThat(presence.add(userId, secondTab)).isFalse();

		assertThat(presence.remove(userId, secondTab)).isFalse();
		assertThat(presence.onlineUserIds()).contains(userId);

		assertThat(presence.remove(userId, firstTab)).isTrue();
	}

	@Test
	void removingASocketThatWasNeverThereDoesNotReportADeparture() {
		// A disconnect for a socket this instance never added -- a restart in the
		// middle of a connection, or a duplicate disconnect handler.
		assertThat(presence.remove(userId, UUID.randomUUID())).isTrue();
		assertThat(presence.onlineUserIds()).doesNotContain(userId);
	}

	@Test
	void aHeartbeatThatStopsTakesTheUserOutOfTheOnlineList() {
		presence.add(userId, UUID.randomUUID());

		// What a kill -9 leaves behind: the entry is still there, its heartbeat
		// simply stops being refreshed. Backdating the score past the window is
		// the same state, without waiting 90 seconds for it.
		redis.opsForZSet().add("presence:online", userId,
				System.currentTimeMillis() / 1000 - RedisPresenceStore.ONLINE_TTL.toSeconds() - 1);

		assertThat(presence.onlineUserIds()).doesNotContain(userId);
	}
}
