package com.wtsend.backend.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.wtsend.backend.models.Conversation;
import com.wtsend.backend.models.ConversationType;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

	@Query("""
			    SELECT c FROM Conversation c
			    JOIN c.participants p
			    WHERE c.type = :type
			      AND p.user.id IN (:user1, :user2)
			    GROUP BY c.id
			    HAVING COUNT(DISTINCT p.user.id) = 2
			""")
	Optional<Conversation> findDirectConversationByUsers(
			@Param("user1") String user1,
			@Param("user2") String user2,
			@Param("type") ConversationType type);

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

}
