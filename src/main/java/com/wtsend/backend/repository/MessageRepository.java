package com.wtsend.backend.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.wtsend.backend.model.Message;

public interface MessageRepository extends JpaRepository<Message, Long> {

	List<Message> findAllByConversation_IdAndCreatedAtBeforeOrderByCreatedAtDesc(Long conversationId, Instant now,
			Pageable pageable);

	List<Message> findByConversation_id(Long conversationId, Pageable pageable);

	List<Message> findByConversation_IdOrderByCreatedAtDesc(Long conversationId,
			Pageable pageable);

}
