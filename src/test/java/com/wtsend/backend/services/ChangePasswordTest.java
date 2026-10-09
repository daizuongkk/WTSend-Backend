package com.wtsend.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.dto.RefreshToken;
import com.wtsend.backend.dto.request.ChangePasswordRequest;
import com.wtsend.backend.dto.response.AuthResponse;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.RefreshTokenRepository;
import com.wtsend.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChangePasswordTest {

	private static final String USER_ID = "user-id";

	@Mock
	private UserRepository userRepository;
	@Mock
	private RefreshTokenRepository refreshTokenRepo;
	@Mock
	private RefreshTokenService refreshTokenService;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private JwtService jwtService;

	@InjectMocks
	private AuthService authService;

	private User userWithPassword(String stored) {
		User user = new User();
		user.setId(USER_ID);
		user.setEmail("owner@example.com");
		user.setPassword(stored);
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
		return user;
	}

	private static ChangePasswordRequest request(String current, String next) {
		ChangePasswordRequest req = new ChangePasswordRequest();
		req.setCurrentPassword(current);
		req.setNewPassword(next);
		return req;
	}

	@Test
	void theDeviceThatChangedThePasswordKeepsWorking() {
		// Every refresh token is revoked, this one included, so returning 204 left
		// the client running until its access token expired and then dropped it at
		// the login screen with no explanation.
		User user = userWithPassword("{bcrypt}old");
		when(passwordEncoder.matches("Current1!", "{bcrypt}old")).thenReturn(true);
		when(passwordEncoder.encode("NewPass1!")).thenReturn("{bcrypt}new");
		when(jwtService.generateToken(user)).thenReturn("access-token");
		when(refreshTokenService.create(user))
				.thenReturn(RefreshToken.builder().token("refresh-token").userId(USER_ID).build());

		AuthResponse res = authService.changePassword(request("Current1!", "NewPass1!"), USER_ID);

		verify(refreshTokenRepo).deleteByUserId(USER_ID);
		assertThat(res.getAccessToken()).isEqualTo("access-token");
		assertThat(res.getRefreshToken()).isEqualTo("refresh-token");
		assertThat(user.getPassword()).isEqualTo("{bcrypt}new");
	}

	@Test
	void aWrongCurrentPasswordChangesNothing() {
		userWithPassword("{bcrypt}old");
		when(passwordEncoder.matches("guess", "{bcrypt}old")).thenReturn(false);

		assertThatThrownBy(() -> authService.changePassword(request("guess", "NewPass1!"), USER_ID))
				.isInstanceOf(AppException.class)
				.extracting(ex -> ((AppException) ex).getErrorCode())
				.isEqualTo(ErrorCode.WRONG_CURRENT_PASSWORD);

		verify(userRepository, never()).save(any());
		verify(refreshTokenRepo, never()).deleteByUserId(any());
	}

	@Test
	void anAccountWithNoPasswordCannotChangeOne() {
		// Created through Google. Setting a first password goes through the reset
		// flow, which proves ownership of the mailbox.
		userWithPassword(null);

		assertThatThrownBy(() -> authService.changePassword(request("anything", "NewPass1!"), USER_ID))
				.isInstanceOf(AppException.class)
				.extracting(ex -> ((AppException) ex).getErrorCode())
				.isEqualTo(ErrorCode.NO_PASSWORD_SET);
	}
}
