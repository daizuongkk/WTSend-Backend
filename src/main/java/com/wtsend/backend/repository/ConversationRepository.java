package com.wtsend.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wtsend.backend.model.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

	/**
	 * Exact lookup of a pair's DIRECT thread by its derived key: one indexed
	 * equality, against the same column the unique index protects. Replaced a
	 * GROUP BY over participants, which could not be constrained.
	 */
	Optional<Conversation> findByDirectKey(String directKey);

	/**
	 * Single conversation with the same graph {@link #findConversations} fetches,
	 * so mapping one does not cost a query per association. Used wherever a
	 * ConversationResponse is built from an id.
	 */
	@Query("""
			    SELECT c FROM Conversation c
			    LEFT JOIN FETCH c.participants p
			    LEFT JOIN FETCH p.user
			    LEFT JOIN FETCH p.lastSeenMessage
			    LEFT JOIN FETCH c.lastMessage lm
			    LEFT JOIN FETCH lm.sender
			    LEFT JOIN FETCH c.group g
			    LEFT JOIN FETCH g.creator
			    WHERE c.id = :id
			""")
	Optional<Conversation> findByIdWithGraph(@Param("id") Long id);

	/**
	 * Every association ConversationMapper reads is fetched here, in one query.
	 * Without the fetch joins this is a textbook N+1: one query for the list,
	 * then per conversation another for its participants, one per participant
	 * for the user, plus lastMessage, its sender, the group and its creator.
	 *
	 * <p>
	 * p.lastSeenMessage is fetched because ParticipantResponse carries its id.
	 * A lazy proxy would in fact hand back an identifier without initialising,
	 * but that is too subtle a guarantee to build on.
	 *
	 * <p>
	 * The membership filter is a separate EXISTS rather than a predicate on the
	 * fetched join -- filtering the same join that fetches the collection would
	 * load only the caller's own participant row and silently drop everyone else
	 * from the conversation.
	 */
	@Query("""
			    SELECT c FROM Conversation c
			    LEFT JOIN FETCH c.participants p
			    LEFT JOIN FETCH p.user
			    LEFT JOIN FETCH p.lastSeenMessage
			    LEFT JOIN FETCH c.lastMessage lm
			    LEFT JOIN FETCH lm.sender
			    LEFT JOIN FETCH c.group g
			    LEFT JOIN FETCH g.creator
			    WHERE EXISTS (
			        SELECT 1 FROM Participant me
			        WHERE me.conversation = c AND me.user.id = :userId
			    )
			    ORDER BY
			        CASE WHEN c.lastMessageAt IS NULL THEN 1 ELSE 0 END,
			        c.lastMessageAt DESC,
			        c.updatedAt DESC
			""")
	List<Conversation> findConversations(@Param("userId") String userId);

	@Query("""
			    SELECT DISTINCT c.id FROM Conversation c
			    JOIN c.participants p
			    WHERE p.user.id = :userId
			""")
	List<Long> getConversationId(@Param("userId") String userId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE Conversation c SET c.lastMessageAt = :time, c.lastMessage.id = :messageId WHERE c.id = :id")
	void updateLastMessage(@Param("id") Long id, @Param("time") Instant time, @Param("messageId") Long messageId);
}
