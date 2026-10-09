package com.wtsend.backend.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.wtsend.backend.event.OnlineVisibilityChangedEvent;
import com.wtsend.backend.socket.PresenceChange;
import com.wtsend.backend.socket.RedisPresenceStore;
import com.wtsend.backend.socket.SocketRelay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies a change to "show my online status" to the presence everyone else
 * reads, and tells the connected clients.
 *
 * <p>
 * It works from any instance without knowing where the user's sockets are:
 * the entry other people read is keyed by user id, not by socket, so the
 * instance handling the HTTP request can add or remove it directly, and the
 * announcement reaches the other instances through the relay.
 *
 * <p>
 * {@code fallbackExecution} because {@code UserService.updateUser} has no
 * transaction of its own -- it makes Cloudinary calls and must not hold a
 * database connection across them -- so it publishes after its save instead.
 * Without this the event would be discarded in silence.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceVisibilityListener {

	private final RedisPresenceStore presence;
	private final SocketRelay relay;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onVisibilityChanged(OnlineVisibilityChangedEvent event) {
		try {
			if (event.isVisible()) {
				// Only announce an arrival if they are actually connected
				// somewhere; turning the switch on while logged out should not
				// light them up for everyone.
				if (presence.show(event.getUserId()))
					relay.toAll("presence-changed", new PresenceChange(event.getUserId(), true));
			} else {
				presence.hide(event.getUserId());
				relay.toAll("presence-changed", new PresenceChange(event.getUserId(), false));
			}
		} catch (Exception e) {
			// After commit there is nothing to roll back, and an exception here
			// would be swallowed by Spring. Worth saying out loud, because the
			// failure mode is a user who asked to be invisible and still is not.
			log.error("Failed to apply online visibility {} for user {}",
					event.isVisible(), event.getUserId(), e);
		}
	}
}
