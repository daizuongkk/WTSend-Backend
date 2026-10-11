package com.wtsend.backend.socket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.wtsend.backend.dto.response.UserResponse;

class PresenceHeartbeatTest {

	private final SocketIOServer server = mock(SocketIOServer.class);
	private final RedisPresenceStore presence = mock(RedisPresenceStore.class);
	private final PresenceAnnouncer announcer = mock(PresenceAnnouncer.class);
	private final PresenceHeartbeat heartbeat = new PresenceHeartbeat(server, presence, announcer);

	@Test
	void everyUserThisInstanceHoldsASocketForIsRefreshed() {
		SocketIOClient client = mock(SocketIOClient.class);
		UserResponse user = new UserResponse();
		user.setId("user-1");
		when(client.get("user")).thenReturn(user);
		when(server.getAllClients()).thenReturn(List.of(client));
		when(presence.pruneStale()).thenReturn(List.of());

		heartbeat.beat();

		// Visibility travels with it: the heartbeat is the only caller, and it is
		// what decides whether a refresh may also re-register.
		verify(presence).touch("user-1", false);
	}

	@Test
	void aUserDroppedByTheSweepIsAnnouncedOffline() {
		// The instance that held them died without running its disconnect
		// handlers. Nobody else will ever say they left, so this is the only
		// moment it can be said -- and a page that is already open will
		// otherwise show them online until it is reloaded.
		when(server.getAllClients()).thenReturn(List.of());
		when(presence.pruneStale()).thenReturn(List.of("gone-1", "gone-2"));

		heartbeat.beat();

		// Through the announcer, which looks up who is entitled to hear it: these
		// users have no socket left, so there is no cached audience to read.
		verify(announcer).announce("gone-1", false);
		verify(announcer).announce("gone-2", false);
	}

	@Test
	void aFailureInOneBeatDoesNotStopTheNextOne() {
		// A @Scheduled method that throws is not rescheduled on some
		// configurations, which would freeze presence for this instance.
		when(server.getAllClients()).thenThrow(new IllegalStateException("redis down"));

		heartbeat.beat();

		verify(announcer, org.mockito.Mockito.never()).announce(any(), anyBoolean());
	}
}
