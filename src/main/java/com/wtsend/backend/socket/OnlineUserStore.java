package com.wtsend.backend.socket;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * Which users have at least one live socket, and which sockets those are.
 *
 * <p>
 * add and remove report whether the user's presence actually changed, not
 * merely whether a socket did. A second tab must not announce the user as newly
 * online, and closing one of two tabs must not announce them as offline --
 * without that distinction the clients see presence flapping.
 *
 * <p>
 * In-process, so this is per-instance: correct for one node only. Phase 3-R
 * replaces it with a shared Redis store.
 */
@Component
@Getter
public class OnlineUserStore {

	private final Map<String, Set<UUID>> store = new ConcurrentHashMap<>();

	/** @return true if this socket brought the user online */
	public boolean add(String userId, UUID socketId) {
		Set<UUID> sockets = store.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
		boolean cameOnline = sockets.isEmpty();
		sockets.add(socketId);
		return cameOnline;
	}

	/** @return true if this was the user's last socket */
	public boolean remove(String userId, UUID socketId) {
		Set<UUID> sockets = store.get(userId);
		if (sockets == null)
			return false;

		sockets.remove(socketId);
		if (sockets.isEmpty()) {
			store.remove(userId);
			return true;
		}
		return false;
	}

	public Set<UUID> getSockets(String userId) {
		return store.getOrDefault(userId, Set.of());
	}

	public Set<String> onlineUserIds() {
		return Set.copyOf(store.keySet());
	}
}
