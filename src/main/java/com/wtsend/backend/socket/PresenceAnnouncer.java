package com.wtsend.backend.socket;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.wtsend.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * The one place that decides who hears about a presence change.
 *
 * <p>
 * It exists because three callers announce them -- a connect or disconnect, the
 * heartbeat sweep, and someone toggling their visibility -- and all three used
 * {@code relay.toAll}. Every connected socket learned when any stranger came
 * online or left, which on an app where accounts are free is a usable activity
 * log on a person you have no relationship with. One class means that cannot be
 * got wrong in one of the three places.
 */
@Component
@RequiredArgsConstructor
public class PresenceAnnouncer {

	private final SocketRelay relay;
	private final UserRepository userRepository;
	private final RedisPresenceStore presence;

	/** Looks the audience up. For callers with no socket to read it from. */
	public void announce(String userId, boolean online) {
		announce(userId, audienceFor(userId), online);
	}

	/** With an audience already in hand, such as one just queried by the caller. */
	public void announce(String userId, List<String> audience, boolean online) {
		if (audience.isEmpty())
			return;

		relay.toRooms(audience.stream().map(SocketRooms::user).toList(),
				"presence-changed", new PresenceChange(userId, online));
	}

	/**
	 * Tells each of these users which of the others is online right now.
	 *
	 * <p>
	 * For the moment a relationship is created, which is a gap the ordinary
	 * announcements cannot cover. A client is given one snapshot at connect and
	 * then deltas, so a socket that was already open when it gained a friend --
	 * or joined a group with somebody it is not friends with -- has no entry for
	 * them and will never get one, because nothing about that person changed.
	 * Both sides showed each other permanently offline in a conversation they
	 * were visibly typing in.
	 *
	 * <p>
	 * Only the online ones are announced. An absent entry already reads as
	 * offline on the client, so saying so would be a round trip to tell somebody
	 * what they already believe.
	 */
	public void announceAmong(Collection<String> userIds) {
		Set<String> linked = new LinkedHashSet<>(userIds);
		if (linked.size() < 2)
			return;

		Set<String> online = presence.onlineUserIds();
		for (String subject : linked) {
			for (String other : linked) {
				if (!other.equals(subject) && online.contains(other))
					announce(other, List.of(subject), true);
			}
		}
	}

	/** Friends plus anyone sharing a conversation; see the repository method. */
	public List<String> audienceFor(String userId) {
		return userRepository.findIdsAllowedToSeePresenceOf(userId);
	}
}
