package com.wtsend.backend.socket;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.Getter;

@Component
@Getter
public class OnlineUserStore {

	private final Map<String, Set<UUID>> store = new ConcurrentHashMap<>();

	public void add(String userId, UUID socketId) {
		store.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(socketId);
	}

	public void remove(String userId, UUID socketId) {
		Set<UUID> sockets = store.get(userId);
		if (sockets != null) {
			sockets.remove(socketId);
			if (sockets.isEmpty()) {
				store.remove(userId);
			}
		}
	}

	public Set<UUID> getSockets(String userId) {
		return store.getOrDefault(userId, Set.of());
	}
}
