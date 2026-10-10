package com.wtsend.backend.socket;

import java.util.List;

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

	/** Looks the audience up. For callers with no socket to read it from. */
	public void announce(String userId, boolean online) {
		announce(userId, audienceFor(userId), online);
	}

	/** With an audience already in hand, such as the one cached on a socket. */
	public void announce(String userId, List<String> audience, boolean online) {
		if (audience.isEmpty())
			return;

		relay.toRooms(audience.stream().map(SocketRooms::user).toList(),
				"presence-changed", new PresenceChange(userId, online));
	}

	/** Friends plus anyone sharing a conversation; see the repository method. */
	public List<String> audienceFor(String userId) {
		return userRepository.findIdsAllowedToSeePresenceOf(userId);
	}
}
