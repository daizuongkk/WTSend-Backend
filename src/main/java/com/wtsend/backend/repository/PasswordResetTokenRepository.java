package com.wtsend.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

import com.wtsend.backend.model.PasswordResetToken;

public interface PasswordResetTokenRepository extends CrudRepository<PasswordResetToken, String> {

  Optional<PasswordResetToken> findByToken(String token);

  Optional<PasswordResetToken> findByUserId(UUID userId);

  void deleteAllByUserId(UUID userId);
}
