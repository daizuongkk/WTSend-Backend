package com.wtsend.backend.repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.wtsend.backend.models.EmailVerificationToken;

public interface EmailVerificationTokenRepository extends CrudRepository<EmailVerificationToken, String> {
	void deleteAllById(String id);

	Optional<EmailVerificationToken> findByUserId(String id);

	Optional<EmailVerificationToken> findByToken(String token);

	void deleteAllByUserId(String id);

	void deleteByToken(String token);
}
