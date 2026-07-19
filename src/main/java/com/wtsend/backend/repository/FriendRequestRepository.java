package com.wtsend.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wtsend.backend.model.FriendRequest;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

	Optional<FriendRequest> findByFromUserIdAndToUserId(String from, String to);

	boolean existsByFromUserIdAndToUserId(String from, String to);

	List<FriendRequest> findByFromUserId(String userId);

	List<FriendRequest> findByToUserId(String userId);
}
