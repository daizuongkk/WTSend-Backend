package com.wtsend.backend.socket;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.wtsend.backend.dto.response.UserResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Re-stamps, for every socket this instance holds, the presence heartbeat the
 * online list is filtered by.
 *
 * <p>
 * Deliberately not under {@code @SchedulerLock}, unlike every other scheduled
 * job here: this is not work to be done once in the cluster. Each instance is
 * reporting the sockets only it knows about, so a lock would silence all but
 * one instance and the others' users would age out of the list while still
 * connected.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceHeartbeat {

	/** Comfortably inside RedisPresenceStore.ONLINE_TTL, so one miss is survivable. */
	static final long INTERVAL_MS = 30_000;

	private final SocketIOServer server;
	private final RedisPresenceStore presence;

	@Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INTERVAL_MS)
	public void beat() {
		try {
			for (SocketIOClient client : server.getAllClients()) {
				UserResponse user = client.get("user");
				if (user != null)
					// touch only refreshes an entry that exists, so a user who has
					// hidden themselves is not quietly put back in the list here.
					presence.touch(user.getId());
			}
			presence.pruneStale();
		} catch (Exception e) {
			// A scheduled method that throws is not rescheduled on some
			// configurations, which would freeze presence for this instance.
			log.error("Presence heartbeat failed", e);
		}
	}
}
