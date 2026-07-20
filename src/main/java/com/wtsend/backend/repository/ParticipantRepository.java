package com.wtsend.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wtsend.backend.model.Participant;
import com.wtsend.backend.model.User;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
	List<Participant> findByUser_id_In(List<String> userId);

	Optional<Participant> findByUser(User user);

	Optional<Participant> findByConversationIdAndUserId(Long id, String userId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE Participant p SET p.unreadCounts = p.unreadCounts + 1 " +
			"WHERE p.conversation.id = :convId AND p.user.id <> :senderId")
	void incrementUnreadCountsForOthers(@Param("convId") Long convId, @Param("senderId") String senderId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE Participant p SET p.unreadCounts = 0 " +
			"WHERE p.conversation.id = :convId AND p.user.id = :senderId")
	void resetUnreadCountForSender(@Param("convId") Long convId, @Param("senderId") String senderId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE Conversation c SET c.lastMessageAt = :time, c.lastMessage.id = :messageId WHERE c.id = :id")
	void updateLastMessage(@Param("id") Long id, @Param("time") Instant time, @Param("messageId") Long messageId);
}
