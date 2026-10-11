package com.wtsend.backend.listener;

import java.util.Collection;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.wtsend.backend.event.ConversationCreatedEvent;
import com.wtsend.backend.event.FriendshipCreatedEvent;
import com.wtsend.backend.socket.PresenceAnnouncer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fills in presence for people who have just become visible to each other.
 *
 * <p>
 * A client gets one presence snapshot at connect and deltas after it, so
 * gaining a friend or joining a group mid-session leaves both sides with no
 * entry for the other and nothing that will ever produce one -- the
 * relationship changed, the presence did not. They showed each other
 * permanently offline until a reconnect.
 *
 * <p>
 * AFTER_COMMIT, like every other emission here: before commit it would announce
 * a relationship that might still roll back.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceLinkListener {

	private final PresenceAnnouncer announcer;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onFriendshipCreated(FriendshipCreatedEvent event) {
		announceQuietly(event.getUserIds());
	}

	/**
	 * Group members are not necessarily friends with each other -- the creator
	 * is checked against each of them, not them against one another -- so a new
	 * conversation can be the thing that makes two people visible to each other.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onConversationCreated(ConversationCreatedEvent event) {
		announceQuietly(event.getRecipientUserIds());
	}

	private void announceQuietly(Collection<String> userIds) {
		try {
			announcer.announceAmong(userIds);
		} catch (Exception e) {
			// Already committed, so there is nothing to roll back and Spring would
			// swallow this silently. Presence being stale is not worth failing the
			// request that caused it, but it is worth a log line.
			log.error("Failed to announce presence among {}", userIds, e);
		}
	}
}
