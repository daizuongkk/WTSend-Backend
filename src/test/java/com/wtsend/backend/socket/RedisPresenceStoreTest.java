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

		assertThat(presence.add(userId, socket, true)).isTrue();
		assertThat(presence.onlineUserIds()).contains(userId);

		assertThat(presence.remove(userId, socket)).isTrue();
		assertThat(presence.onlineUserIds()).doesNotContain(userId);
	}

	@Test
	void aSecondTabIsNotAnArrivalAndClosingItIsNotADeparture() {
		UUID firstTab = UUID.randomUUID();
		UUID secondTab = UUID.randomUUID();

		assertThat(presence.add(userId, firstTab, true)).isTrue();
		assertThat(presence.add(userId, secondTab, true)).isFalse();

		assertThat(presence.remove(userId, secondTab)).isFalse();
		assertThat(presence.onlineUserIds()).contains(userId);

		assertThat(presence.remove(userId, firstTab)).isTrue();
	}

	@Test
	void removingASocketThatWasNeverThereStillReportsADeparture() {
		// A disconnect for a socket this instance never added -- a restart in the
		// middle of a connection, or a duplicate disconnect handler. It reports a
		// departure on purpose: no sockets are left, and announcing an offline
		// nobody was waiting for costs nothing, while staying silent would leave
		// the user shown online after they left.
		assertThat(presence.remove(userId, UUID.randomUUID())).isTrue();
		assertThat(presence.onlineUserIds()).doesNotContain(userId);
	}

	@Test
	void aUserWhoHidesTheirStatusIsNeverListed() {
		assertThat(presence.add(userId, UUID.randomUUID(), false)).isTrue();

		// Their socket is still counted -- closing one of two tabs must not look
		// like their last one -- they are simply not in the list others read.
		assertThat(presence.onlineUserIds()).doesNotContain(userId);
	}

	@Test
	void hidingTakesEffectAtOnceAndTheHeartbeatDoesNotUndoIt() {
		presence.add(userId, UUID.randomUUID(), true);
		assertThat(presence.onlineUserIds()).contains(userId);

		presence.hide(userId);
		assertThat(presence.onlineUserIds()).doesNotContain(userId);

		// The heartbeat refreshes entries; it must not recreate this one, or
		// hiding would quietly reverse itself within 30 seconds. That is what the
		// visibility flag buys: false means refresh an entry, never create one.
		presence.touch(userId, false);
		assertThat(presence.onlineUserIds()).doesNotContain(userId);
	}

	@Test
	void aVisibleUserPrunedByMistakeIsPutBackByTheirOwnHeartbeat() {
		presence.add(userId, UUID.randomUUID(), true);

		// What a heartbeat later than the 90s read window does: pruneStale drops
		// the entry even though the socket is still open. With ZADD XX on every
		// touch, nothing could ever put it back and the user stayed invisible for
		// the rest of their socket's life -- one late sweep, permanent.
		redis.opsForZSet().remove("presence:online", userId);
		assertThat(presence.onlineUserIds()).doesNotContain(userId);

		presence.touch(userId, true);
		assertThat(presence.onlineUserIds()).contains(userId);
	}

	@Test
	void showingAgainListsAConnectedUserAndIgnoresADisconnectedOne() {
		UUID socket = UUID.randomUUID();
		presence.add(userId, socket, false);

		assertThat(presence.show(userId)).isTrue();
		assertThat(presence.onlineUserIds()).contains(userId);

		// Switching the setting on while logged out must not light the user up
		// for everyone.
		presence.remove(userId, socket);
		presence.hide(userId);
		assertThat(presence.show(userId)).isFalse();
		assertThat(presence.onlineUserIds()).doesNotContain(userId);
	}

	@Test
	void aUserComingBackAfterACrashIsStillReportedAsArriving() {
		// What an instance killed with -9 leaves behind: a socket member nobody
		// will ever remove, under a dead instance's id, alive until the set's own
		// TTL. The user then reconnects somewhere else.
		redis.opsForSet().add("presence:sockets:" + userId, "dead-instance:" + UUID.randomUUID());

		// The arrival has to be announced anyway. Deciding it from the socket
		// count made the ghost look like an existing tab, so nobody was told the
		// user was back and every open page kept showing them offline.
		assertThat(presence.add(userId, UUID.randomUUID(), true)).isTrue();
		assertThat(presence.onlineUserIds()).contains(userId);
	}

	@Test
	void pruningReportsWhoItRemovedSoTheyCanBeAnnouncedOffline() {
		presence.add(userId, UUID.randomUUID(), true);
		redis.opsForZSet().add("presence:online", userId,
				System.currentTimeMillis() / 1000 - RedisPresenceStore.ONLINE_TTL.toSeconds() - 1);

		// Dropping them from the list is not enough: a page that is already open
		// never re-reads it, so unless the prune says who it removed there is
		// nobody to send presence-changed(false) about.
		assertThat(presence.pruneStale()).contains(userId);
		assertThat(presence.onlineUserIds()).doesNotContain(userId);
	}

	@Test
	void aHeartbeatThatStopsTakesTheUserOutOfTheOnlineList() {
		presence.add(userId, UUID.randomUUID(), true);

		// What a kill -9 leaves behind: the entry is still there, its heartbeat
		// simply stops being refreshed. Backdating the score past the window is
		// the same state, without waiting 90 seconds for it.
		redis.opsForZSet().add("presence:online", userId,
				System.currentTimeMillis() / 1000 - RedisPresenceStore.ONLINE_TTL.toSeconds() - 1);

		assertThat(presence.onlineUserIds()).doesNotContain(userId);
	}
}
