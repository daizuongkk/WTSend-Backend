package com.wtsend.backend.socket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.wtsend.backend.repository.UserRepository;

/**
 * Who hears about a presence change, and who hears about a new relationship.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresenceAnnouncerTest {

	@Mock
	private SocketRelay relay;
	@Mock
	private UserRepository userRepository;
	@Mock
	private RedisPresenceStore presence;

	@InjectMocks
	private PresenceAnnouncer announcer;

	@Test
	void anAnnouncementGoesToTheAudiencesPersonalRoomsOnly() {
		when(userRepository.findIdsAllowedToSeePresenceOf("alice"))
				.thenReturn(List.of("bob", "carol"));

		announcer.announce("alice", true);

		// Personal rooms, not every socket. relay.toAll meant any account could
		// watch any stranger come and go.
		verify(relay).toRooms(eq(List.of("user:bob", "user:carol")),
				eq("presence-changed"), eq(new PresenceChange("alice", true)));
	}

	@Test
	void anAudienceOfNobodyIsNotBroadcastToAnybody() {
		announcer.announce("alice", List.of(), true);

		verifyNoInteractions(relay);
	}

	@Test
	void aNewFriendshipTellsEachSideTheOtherIsOnline() {
		when(presence.onlineUserIds()).thenReturn(Set.of("alice", "bob"));

		announcer.announceAmong(List.of("alice", "bob"));

		// Both directions. A client gets one snapshot at connect and deltas
		// after it, so neither of them had an entry for the other and nothing
		// would ever produce one -- the relationship changed, the presence did
		// not, and they showed each other offline in their own conversation.
		verify(relay).toRooms(eq(List.of("user:bob")), eq("presence-changed"),
				eq(new PresenceChange("alice", true)));
		verify(relay).toRooms(eq(List.of("user:alice")), eq("presence-changed"),
				eq(new PresenceChange("bob", true)));
	}

	@Test
	void anOfflineCounterpartIsNotAnnounced() {
		when(presence.onlineUserIds()).thenReturn(Set.of("alice"));

		announcer.announceAmong(List.of("alice", "bob"));

		// Bob is offline, so Alice is told nothing: an absent entry already
		// reads as offline, and saying so is a round trip to confirm what the
		// client already believes.
		verify(relay).toRooms(eq(List.of("user:bob")), eq("presence-changed"),
				eq(new PresenceChange("alice", true)));
		verify(relay, never()).toRooms(eq(List.of("user:alice")), any(), any());
	}

	@Test
	void aGroupTellsEveryOnlineMemberAboutEveryOtherOnlineMember() {
		when(presence.onlineUserIds()).thenReturn(Set.of("alice", "bob", "carol"));

		announcer.announceAmong(List.of("alice", "bob", "carol"));

		// Group members are not necessarily friends with one another -- the
		// creator is checked against each of them, not them against each other --
		// so the conversation is what makes some of these pairs visible.
		for (String subject : List.of("alice", "bob", "carol")) {
			for (String other : List.of("alice", "bob", "carol")) {
				if (!subject.equals(other))
					verify(relay).toRooms(eq(List.of(SocketRooms.user(subject))),
							eq("presence-changed"), eq(new PresenceChange(other, true)));
			}
		}
	}

	@Test
	void oneUserAloneIsNobodyToTell() {
		announcer.announceAmong(List.of("alice"));

		verifyNoInteractions(relay, presence);
	}
}
