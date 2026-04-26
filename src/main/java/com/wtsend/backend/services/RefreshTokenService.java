package com.wtsend.backend.services;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.wtsend.backend.dtos.RefreshToken;
import com.wtsend.backend.exceptions.RefreshTokenException;
import com.wtsend.backend.models.User;
import com.wtsend.backend.repositories.RefreshTokenRepository;
import com.wtsend.backend.repositories.UserRepository;

@Service
public class RefreshTokenService {
	@Value("${jwt.refresh-token.ttl}")
	private Long REFRESH_TOKEN_TTL;
	private final RefreshTokenRepository refreshTokenRepo;

	private final UserRepository userRepo;

	RefreshTokenService(RefreshTokenRepository refreshTokenRepo, UserRepository userRepo) {
		this.refreshTokenRepo = refreshTokenRepo;
		this.userRepo = userRepo;
	}

	public User verify(String token) {

		if (token == null || token.isBlank())
			throw new RefreshTokenException("Invalid token");

		RefreshToken rfToken = refreshTokenRepo.findById(token)
				.orElseThrow(() -> new RefreshTokenException("Token is invalid or expired"));

		User user = userRepo.findById(rfToken.getUserId())
				.orElseThrow(() -> new RefreshTokenException("User not found by id: " + rfToken.getUserId()));

		refreshTokenRepo.delete(rfToken);
		return user;
	}

	public RefreshToken create(User user) {

		String newRefreshToken = UUID.randomUUID().toString();

		return refreshTokenRepo
				.save(RefreshToken.builder().token(newRefreshToken).userId(user.getId()).expiresIn(REFRESH_TOKEN_TTL).build());
	}

}
