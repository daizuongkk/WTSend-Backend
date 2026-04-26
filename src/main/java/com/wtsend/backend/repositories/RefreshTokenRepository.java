package com.wtsend.backend.repositories;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.wtsend.backend.dtos.RefreshToken;

@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {
	Optional<RefreshToken> findByToken(String token);

	Optional<RefreshToken> findByUserId(String userId);

	void deleteByUserId(String userId);
}
