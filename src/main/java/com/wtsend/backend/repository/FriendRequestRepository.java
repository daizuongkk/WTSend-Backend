package com.wtsend.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wtsend.backend.model.FriendRequest;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

	boolean existsByFromUserIdAndToUserId(String from, String to);

	/**
	 * Both sides fetched: getFriendRequests maps every row to a pair of
	 * UserResponse, which was two queries per pending request.
	 */
	@Query("SELECT r FROM FriendRequest r JOIN FETCH r.fromUser JOIN FETCH r.toUser WHERE r.fromUser.id = :userId")
	List<FriendRequest> findByFromUserId(@Param("userId") String userId);

	@Query("SELECT r FROM FriendRequest r JOIN FETCH r.fromUser JOIN FETCH r.toUser WHERE r.toUser.id = :userId")
	List<FriendRequest> findByToUserId(@Param("userId") String userId);
}
