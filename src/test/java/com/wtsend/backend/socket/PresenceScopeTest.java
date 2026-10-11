package com.wtsend.backend.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.corundumstudio.socketio.SocketIOClient;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.services.ConversationService;
import com.wtsend.backend.services.UserService;

/**
 * Who is told that somebody came online or left.
 *
 * <p>
 * It used to be everybody: the snapshot was the whole online list and the
 * delta went through {@code relay.toAll}, so a brand new account could watch
 * every stranger using the app come and go. Accounts are free, so that is an
 * activity log on anyone, for anyone.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresenceScopeTest {

	private static final String ALICE = "alice";

	@Mock
	private UserService userService;
	@Mock
	private RedisPresenceStore onlineUsers;
	@Mock
	private ConversationService conversationService;
	@Mock
	private PresenceAnnouncer announcer;

	@InjectMocks
	private UserSessionService userSession;

	@Mock
	private SocketIOClient client;

	private UserResponse alice;

	@BeforeEach
	void setUp() {
		alice = new UserResponse();
		alice.setId(ALICE);
		alice.setUsername("alice");
		alice.setShowOnlineStatus(true);

		when(client.getSessionId()).thenReturn(UUID.randomUUID());
		when(userService.findById(ALICE)).thenReturn(alice);
		when(conversationService.getConversationIdsByUserId(ALICE)).thenReturn(List.of());
	}

	private void connect() {
		userSession.onClientConnected(ALICE, client);
	}

	@Test
	void theSnapshotHoldsOnlyPeopleTheArrivingUserMaySee() {
		when(announcer.audienceFor(ALICE)).thenReturn(List.of("friend-1", "friend-2"));
		when(onlineUsers.onlineUserIds()).thenReturn(Set.of("friend-1", "a-stranger", ALICE));
		when(onlineUsers.add(eq(ALICE), any(), eq(true))).thenReturn(true);

		connect();

		ArgumentCaptor<Object> snapshot = ArgumentCaptor.forClass(Object.class);
		verify(client).sendEvent(eq("online-users"), snapshot.capture());

		// friend-2 is not online, a-stranger is nothing to Alice.
		@SuppressWarnings("unchecked")
		Set<String> visible = (Set<String>) snapshot.getValue();
		assertThat(visible).containsExactly("friend-1");
	}

	@Test
	void anArrivalIsAnnouncedToThatAudienceAndNobodyElse() {
		List<String> audience = List.of("friend-1");
		when(announcer.audienceFor(ALICE)).thenReturn(audience);
		when(onlineUsers.onlineUserIds()).thenReturn(Set.of());
		when(onlineUsers.add(eq(ALICE), any(), eq(true))).thenReturn(true);

		connect();

		verify(announcer).announce(ALICE, audience, true);
	}

	@Test
	void auserWithNobodyToTellIsNotAnnouncedAtAll() {
		when(announcer.audienceFor(ALICE)).thenReturn(List.of());
		when(onlineUsers.onlineUserIds()).thenReturn(Set.of());
		when(onlineUsers.add(eq(ALICE), any(), eq(true))).thenReturn(true);

		connect();

		// An account with no friends and no conversations: there is no audience,
		// and the announcer is what refuses to broadcast to one.
		verify(announcer).announce(eq(ALICE), eq(List.of()), eq(true));
	}

	@Test
	void aHiddenUserIsStillNotAnnounced() {
		alice.setShowOnlineStatus(false);
		when(announcer.audienceFor(ALICE)).thenReturn(List.of("friend-1"));
		when(onlineUsers.onlineUserIds()).thenReturn(Set.of());
		when(onlineUsers.add(eq(ALICE), any(), eq(false))).thenReturn(true);

		connect();

		verify(announcer, never()).announce(any(), anyList(), anyBoolean());
	}

	@Test
	void aHandshakeThatCarriedNoIdentityIsDisconnected() {
		userSession.onClientConnected(null, client);

		verify(client).disconnect();
		verify(userService, never()).findById(any());
	}
}
