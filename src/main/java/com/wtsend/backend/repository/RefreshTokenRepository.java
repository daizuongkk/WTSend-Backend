package com.wtsend.backend.repository;


import org.springframework.data.repository.CrudRepository;

import com.wtsend.backend.dto.RefreshToken;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {
	void deleteByUserId(String userId);
}
