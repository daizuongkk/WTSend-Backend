package com.wtsend.backend.repositories;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.wtsend.backend.dtos.RefreshToken;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {
	Optional<RefreshToken> findByToken(String token);

	Optional<RefreshToken> findByUserId(String userId);

	void deleteByUserId(String userId);
}
