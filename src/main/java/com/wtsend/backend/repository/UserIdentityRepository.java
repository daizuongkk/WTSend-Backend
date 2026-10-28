package com.wtsend.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.wtsend.backend.model.UserIdentity;
import com.wtsend.backend.model.enums.AuthProvider;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

  /**
   * The lookup a provider login starts from.
   *
   * <p>
   * {@code JOIN FETCH} because the caller returns the user straight away and
   * {@code open-in-view} is off -- a lazy load would happen outside any session.
   */
  @Query("SELECT i FROM UserIdentity i JOIN FETCH i.user "
      + "WHERE i.provider = :provider AND i.providerUserId = :subject")
  Optional<UserIdentity> findBySubject(@Param("provider") AuthProvider provider,
      @Param("subject") String subject);

  /**
   * At most one per provider, enforced by {@code uk_user_identity_user_provider}.
   */
  Optional<UserIdentity> findByUser_IdAndProvider(UUID userId, AuthProvider provider);
}
