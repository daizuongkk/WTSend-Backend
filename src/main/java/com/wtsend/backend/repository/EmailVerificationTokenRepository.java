package com.wtsend.backend.repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.wtsend.backend.model.EmailVerificationToken;

public interface EmailVerificationTokenRepository extends CrudRepository<EmailVerificationToken, String> {
	Optional<EmailVerificationToken> findByUserId(String id);

	Optional<EmailVerificationToken> findByToken(String token);

	void deleteAllByUserId(String id);

}
