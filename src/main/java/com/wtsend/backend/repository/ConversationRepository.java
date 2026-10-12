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

	Optional<Conversation> findByDirectKey(String directKey);


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
