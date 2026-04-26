package com.wtsend.backend.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.wtsend.backend.models.Participant;
import com.wtsend.backend.models.User;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Long> {
	List<Participant> findByUser_id_In(List<String> userId);

	Optional<Participant> findByUser(User user);

	Optional<Participant> findByConversationIdAndUserId(Long id, String userId);
}
