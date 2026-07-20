package com.wtsend.backend.services;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.dto.RefreshToken;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.RefreshTokenRepository;
import com.wtsend.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
	@Value("${jwt.refresh-token.ttl}")
	private Long REFRESH_TOKEN_TTL;
	private final RefreshTokenRepository refreshTokenRepo;

	private final UserRepository userRepo;

	public User verify(String token) {

		if (token == null || token.isBlank())
			throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);

		RefreshToken rfToken = refreshTokenRepo.findById(token)
				.orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_INVALID));

		return userRepo.findById(rfToken.getUserId())
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)
						.withDetail("id=" + rfToken.getUserId() + " (referenced by a live refresh token)"));
	}

	public RefreshToken create(User user) {

		String newRefreshToken = UUID.randomUUID().toString();

		return refreshTokenRepo
				.save(RefreshToken.builder().token(newRefreshToken).userId(user.getId()).expiresIn(REFRESH_TOKEN_TTL).build());
	}

	/**
	 * Deletes the presented token only, leaving the user's other sessions alone.
	 * Idempotent: revoking an already-gone token is not an error.
	 */
	public void revoke(String token) {
		if (token == null || token.isBlank())
			return;

		refreshTokenRepo.deleteById(token);
	}

}
