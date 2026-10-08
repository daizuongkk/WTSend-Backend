package com.wtsend.backend.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wtsend.backend.model.Message;

public interface MessageRepository extends JpaRepository<Message, Long> {

	/**
	 * Newest page of a conversation. Ordered by {@code (createdAt, id)} so the
	 * sort is total -- see {@link com.wtsend.backend.libs.MessageCursor}.
	 */
	@Query("""
			    SELECT m FROM Message m
			    WHERE m.conversation.id = :conversationId
			    ORDER BY m.createdAt DESC, m.id DESC
			""")
	List<Message> findNewestPage(@Param("conversationId") Long conversationId, Pageable pageable);

	/**
	 * The page strictly older than {@code (createdAt, id)}. The compound
	 * comparison is what stops messages sharing a timestamp from being skipped
	 * or repeated at a page boundary.
	 */
	@Query("""
			    SELECT m FROM Message m
			    WHERE m.conversation.id = :conversationId
			      AND (m.createdAt < :createdAt
			           OR (m.createdAt = :createdAt AND m.id < :id))
			    ORDER BY m.createdAt DESC, m.id DESC
			""")
	List<Message> findPageOlderThan(@Param("conversationId") Long conversationId,
			@Param("createdAt") Instant createdAt,
			@Param("id") Long id,
			Pageable pageable);
}
