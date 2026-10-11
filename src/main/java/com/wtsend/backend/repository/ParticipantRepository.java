package com.wtsend.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wtsend.backend.model.Message;
import com.wtsend.backend.model.Participant;
import com.wtsend.backend.model.User;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
	Optional<Participant> findByConversationIdAndUserId(Long id, String userId);

	/**
	 * COALESCE, not a bare {@code + 1}: the column is nullable and NULL + 1 is
	 * NULL in SQL, so a row that ever held NULL would be pinned there -- every
	 * later increment a no-op and that conversation's badge dead for good.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE Participant p SET p.unreadCounts = COALESCE(p.unreadCounts, 0) + 1 " +
			"WHERE p.conversation.id = :convId AND p.user.id <> :senderId")
	void incrementUnreadCountsForOthers(@Param("convId") Long convId, @Param("senderId") String senderId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE Participant p SET p.unreadCounts = 0 " +
			"WHERE p.conversation.id = :convId AND p.user.id = :senderId")
	void resetUnreadCountForSender(@Param("convId") Long convId, @Param("senderId") String senderId);

	/**
	 * Marks one participant's view of a conversation as read, in one statement.
	 *
	 * <p>
	 * It replaces a read-modify-write that raced
	 * {@link #incrementUnreadCountsForOthers}: the reader loaded the row, a
	 * sender's transaction committed {@code unreadCounts + 1} and a newer
	 * lastMessage, and then the reader flushed a stale 0 over it with the OLD
	 * lastSeenMessage. The badge cleared for a message that was never shown, and
	 * the read receipt told the sender it had been seen. Participant carries no
	 * {@code @Version}, so that was a silent lost update rather than a failure.
	 *
	 * <p>
	 * {@code clearAutomatically} detaches everything the caller is holding, so a
	 * caller that needs to report the new state has to read it back afterwards --
	 * the detached copy keeps its pre-update values and gives no hint that it is
	 * stale. See {@code ConversationService.markAsSeen}.
	 *
	 * @return rows updated -- 0 means the caller is not a participant
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			    UPDATE Participant p
			       SET p.unreadCounts = 0,
			           p.lastSeenMessage = :lastSeen,
			           p.lastReadAt = :readAt
			     WHERE p.conversation.id = :convId
			       AND p.user.id = :userId
			""")
	int markSeen(@Param("convId") Long convId, @Param("userId") String userId,
			@Param("lastSeen") Message lastSeen, @Param("readAt") Instant readAt);
}
