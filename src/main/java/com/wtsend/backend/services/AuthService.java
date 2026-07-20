package com.wtsend.backend.services;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.wtsend.backend.dto.RefreshToken;
import com.wtsend.backend.dto.request.ChangePasswordRequest;
import com.wtsend.backend.dto.request.SignInRequest;
import com.wtsend.backend.dto.request.SignUpRequest;
import com.wtsend.backend.dto.response.AuthResponse;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.RefreshTokenRepository;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.interfaces.IAuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService implements IAuthService {

	private final UserRepository userRepository;
	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final RefreshTokenRepository refreshTokenRepo;
	private final UserUtils userMapper;
	private final PasswordEncoder passwordEncoder;
	private final GoogleAuthService googleAuthService;
	private final EmailService emailService;

	public AuthResponse signIn(SignInRequest req) {
		UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
				req.getEmail().trim(), req.getPassword());

		Authentication auth = authenticationManager.authenticate(usernamePasswordAuthenticationToken);

		User user = (User) auth.getPrincipal();

		if (!user.isEmailVerified()) {

			emailService.sendVerifyLink(user);
			return AuthResponse.builder().emailVerified(false).accessToken(jwtService.generateToken(user))
					.email(user.getEmail()).build();
		}

		RefreshToken newRefreshToken = refreshTokenService.create(user);
		return AuthResponse.builder().emailVerified(user.isEmailVerified()).accessToken(jwtService.generateToken(user))
				.refreshToken(newRefreshToken.getToken())
				.build();
	}

	public AuthResponse googleLogin(String token) {

		GoogleIdToken.Payload oauthPayload = googleAuthService.verify(token);
		log.info(oauthPayload.getEmail());

		User user = userRepository.findByEmail(oauthPayload.getEmail())
				.orElseGet(() -> userRepository.save(userMapper.googleOAuth2PayloadToEntity(oauthPayload)));

		return AuthResponse.builder()
				.accessToken(jwtService.generateToken(user))
				.refreshToken(refreshTokenService.create(user).getToken())
				.build();
	}

	/**
	 * Revokes only the presented refresh token. Keying off the user id would pick
	 * an arbitrary one of their sessions and log out the wrong device, and reading
	 * the access token would make logout impossible once that token expires.
	 */
	public String signOut(String refreshToken) {
		refreshTokenService.revoke(refreshToken);
		return "Signout successfully";
	}

	@Transactional
	public UserResponse signUp(SignUpRequest request) {

		if (userRepository.existsByEmail(request.getEmail())) {
			throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}

		User newUser = userMapper.toUser(request);

		// Save first: the id is generated on persist, and sendVerifyLink stores a
		// token keyed by it.
		User savedUser = userRepository.save(newUser);
		emailService.sendVerifyLink(savedUser);

		return userMapper.toUserResponse(savedUser);

	}

	public AuthResponse refreshToken(String token) {

		User user = refreshTokenService.verify(token);
		refreshTokenService.revoke(token);

		RefreshToken newRfToken = refreshTokenService.create(user);

		String newAccessToken = jwtService.generateToken(user);
		return AuthResponse.builder()
				.refreshToken(newRfToken.getToken())
				.accessToken(newAccessToken)
				.build();
	}

	@Override
	@Transactional
	public void changePassword(ChangePasswordRequest request, String userId) {
		// This app is an OAuth2 resource server, so the principal is a Jwt, not a
		// User -- casting the SecurityContext principal here threw ClassCastException.
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + userId));

		if (user.getPassword() == null)
			throw new AppException(ErrorCode.NO_PASSWORD_SET).withDetail("userId=" + userId);

		if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
			throw new AppException(ErrorCode.WRONG_CURRENT_PASSWORD).withDetail("userId=" + userId);

		}

		user.setPassword(passwordEncoder.encode(request.getNewPassword()));
		userRepository.save(user);

		// Any session still holding an old refresh token must not survive.
		refreshTokenRepo.deleteByUserId(userId);
	}

}
