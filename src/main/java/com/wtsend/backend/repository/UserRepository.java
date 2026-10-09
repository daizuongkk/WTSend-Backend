package com.wtsend.backend.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wtsend.backend.model.User;

public interface UserRepository extends JpaRepository<User, String> {

	Optional<User> findByUsername(String username);

	boolean existsByUsername(String username);

	boolean existsByEmail(String email);

	/**
	 * Deletes registrations that were never verified, are past the cutoff, and own
	 * nothing at all.
	 *
	 * <p>
	 * One statement rather than read-then-delete, so two instances racing on the
	 * same window cannot both act on the same row: the second simply deletes
	 * nothing. Safe to run twice.
	 *
	 * <p>
	 * emailVerified = false means exactly one thing, because the address is not
	 * updatable -- see UserService.updateUser. Were it updatable, this query
	 * would also match every established account that had just changed address.
	 *
	 * <p>
	 * The NOT EXISTS clauses are not belt and braces. An unverified account
	 * cannot own anything today, but only because SecurityConfig gates all of
	 * /api/** behind EMAIL_VERIFIED -- the moment any endpoint is opened up, an
	 * unconditional delete starts failing on a foreign key, inside a scheduled
	 * job where the only symptom is a log line. They also state the intent
	 * exactly: an account with data is not an abandoned registration.
	 *
	 * @return how many were removed
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			    DELETE FROM User u
			    WHERE u.emailVerified = false
			      AND u.createdAt < :cutoff
			      AND NOT EXISTS (SELECT 1 FROM Message m WHERE m.sender = u)
			      AND NOT EXISTS (SELECT 1 FROM Participant p WHERE p.user = u)
			      AND NOT EXISTS (SELECT 1 FROM Friend f WHERE f.userA = u OR f.userB = u)
			      AND NOT EXISTS (
			        SELECT 1 FROM FriendRequest fr WHERE fr.fromUser = u OR fr.toUser = u
			      )
			      AND NOT EXISTS (SELECT 1 FROM GroupInfo g WHERE g.creator = u)
			""")
	int deleteUnverifiedRegisteredBefore(@Param("cutoff") Instant cutoff);

	Optional<User> findByEmail(String email);
}
