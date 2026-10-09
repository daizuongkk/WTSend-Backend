package com.wtsend.backend.socket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
	private final SocketRelay relay = mock(SocketRelay.class);
	private final PresenceHeartbeat heartbeat = new PresenceHeartbeat(server, presence, relay);

	@Test
	void everyUserThisInstanceHoldsASocketForIsRefreshed() {
		SocketIOClient client = mock(SocketIOClient.class);
		UserResponse user = new UserResponse();
		user.setId("user-1");
		when(client.get("user")).thenReturn(user);
		when(server.getAllClients()).thenReturn(List.of(client));
		when(presence.pruneStale()).thenReturn(List.of());

		heartbeat.beat();

		verify(presence).touch("user-1");
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

		verify(relay).toAll(eq("presence-changed"), eq(new PresenceChange("gone-1", false)));
		verify(relay).toAll(eq("presence-changed"), eq(new PresenceChange("gone-2", false)));
	}

	@Test
	void aFailureInOneBeatDoesNotStopTheNextOne() {
		// A @Scheduled method that throws is not rescheduled on some
		// configurations, which would freeze presence for this instance.
		when(server.getAllClients()).thenThrow(new IllegalStateException("redis down"));

		heartbeat.beat();

		verify(relay, org.mockito.Mockito.never()).toAll(any(), any());
	}
}
