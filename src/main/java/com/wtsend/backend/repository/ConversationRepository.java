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

	@Query("""
			    SELECT DISTINCT c FROM Conversation c
			    JOIN c.participants p
			    WHERE p.user.id = :userId
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
