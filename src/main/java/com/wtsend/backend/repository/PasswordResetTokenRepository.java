package com.wtsend.backend.repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.wtsend.backend.model.PasswordResetToken;

public interface PasswordResetTokenRepository extends CrudRepository<PasswordResetToken, String> {

	Optional<PasswordResetToken> findByToken(String token);

	Optional<PasswordResetToken> findByUserId(String userId);

	void deleteAllByUserId(String userId);
}
