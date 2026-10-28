package com.wtsend.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

import com.wtsend.backend.model.EmailVerificationToken;

public interface EmailVerificationTokenRepository extends CrudRepository<EmailVerificationToken, String> {
  Optional<EmailVerificationToken> findByUserId(UUID id);

  Optional<EmailVerificationToken> findByToken(String token);

  void deleteAllByUserId(UUID id);

}
