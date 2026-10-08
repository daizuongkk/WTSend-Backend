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
	 * Deletes registrations that were never verified and are past the cutoff.
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
	 * @return how many were removed
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			    DELETE FROM User u
			    WHERE u.emailVerified = false
			      AND u.createdAt < :cutoff
			""")
	int deleteUnverifiedRegisteredBefore(@Param("cutoff") Instant cutoff);

	Optional<User> findByEmail(String email);
}
