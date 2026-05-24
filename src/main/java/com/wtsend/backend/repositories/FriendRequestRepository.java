package com.wtsend.backend.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wtsend.backend.models.FriendRequest;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

	Optional<FriendRequest> findByFromUserIdAndToUserId(String from, String to);

	boolean existsByFromUserIdAndToUserId(String from, String to);

	List<FriendRequest> findByFromUserId(String userId);

	List<FriendRequest> findByToUserId(String userId);
}
