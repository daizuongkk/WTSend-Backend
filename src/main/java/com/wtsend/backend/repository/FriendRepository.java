package com.wtsend.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wtsend.backend.model.Friend;
import com.wtsend.backend.model.User;

public interface FriendRepository extends JpaRepository<Friend, Long> {

	Optional<Friend> findByUserAIdAndUserBId(String from, String to);

	boolean existsByUserAIdAndUserBId(String from, String to);

	List<Friend> findByUserAIdOrUserBId(String from, String to);

	@Query("""
			    SELECT f FROM Friend f
			    WHERE f.userA.id = :userId
			       OR f.userB.id = :userId
			""")
	List<Friend> findFriends(@Param("userId") String userId);

	@Query("""
			    SELECT COUNT(f) > 0 FROM Friend f
			    WHERE
			        (f.userA.id = :userId1 AND f.userB.id = :userId2)
			        OR
			        (f.userA.id = :userId2 AND f.userB.id = :userId1)
			""")
	boolean existsFriendship(
			@Param("userId1") String userId1,
			@Param("userId2") String userId2);

	/**
	 * The ids from {@code others} that are NOT friends with {@code userId}.
	 * One query instead of one per member, and it names the offenders so the
	 * caller can report which one failed.
	 */
	@Query("""
			    SELECT other FROM User other
			    WHERE other.id IN :others
			      AND NOT EXISTS (
			        SELECT 1 FROM Friend f
			        WHERE (f.userA.id = :userId AND f.userB.id = other.id)
			           OR (f.userA.id = other.id AND f.userB.id = :userId)
			      )
			""")
	List<User> findNonFriendsAmong(@Param("userId") String userId, @Param("others") List<String> others);
}
