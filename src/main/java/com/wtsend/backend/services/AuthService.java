package com.wtsend.backend.services;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.wtsend.backend.dto.RefreshToken;
import com.wtsend.backend.dto.TokenPayload;
import com.wtsend.backend.dto.request.ChangePasswordRequest;
import com.wtsend.backend.dto.request.SignInRequest;
import com.wtsend.backend.dto.request.SignUpRequest;
import com.wtsend.backend.dto.response.AuthResponse;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.exceptions.DuplicateResourceException;
import com.wtsend.backend.exceptions.RequestException;
import com.wtsend.backend.exceptions.ResourceNotFoundException;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.models.User;
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

	public AuthResponse googleLogin(String provider, String token) {

		GoogleIdToken.Payload oauthPayload = googleAuthService.verify(token);
		log.info(oauthPayload.getEmail());

		User user = userRepository.findByEmail(oauthPayload.getEmail())
				.orElseGet(() -> userRepository.save(userMapper.googleOAuth2PayloadToEntity(oauthPayload)));

		return AuthResponse.builder()
				.accessToken(jwtService.generateToken(user))
				.refreshToken(refreshTokenService.create(user).getToken())
				.build();
	}

	public String signOut(String token) {
		TokenPayload payload = jwtService.parseToken(token.replace("Bearer ", "").trim());

		String userId = payload.getSub();

		RefreshToken deletedToken = refreshTokenRepo.findByUserId(userId)
				.orElseThrow(() -> new ResourceNotFoundException("Not refresh token by userId: " + userId));

		refreshTokenRepo.delete(deletedToken);
		return "Signout successfully";
	}

	public UserResponse signUp(SignUpRequest request) {

		if (userRepository.existsByEmail(request.getEmail())) {
			throw new DuplicateResourceException("Email already exists");
		}

		User newUser = userMapper.toUser(request);

		emailService.sendVerifyLink(newUser);
		return userMapper.toUserResponse(userRepository.save(newUser));

	}

	public AuthResponse refreshToken(String token) {

		User user = refreshTokenService.verify(token);
		refreshTokenRepo.deleteByUserId(user.getId());

		RefreshToken newRfToken = refreshTokenService.create(user);

		String newAccessToken = jwtService.generateToken(user);
		return AuthResponse.builder()
				.refreshToken(newRfToken.getToken())
				.accessToken(newAccessToken)
				.build();
	}

	@Override
	public void changePassword(ChangePasswordRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		User user = (User) auth.getPrincipal();

		if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
			throw new RequestException("Wrong current password");

		}

		user.setPassword(passwordEncoder.encode(request.getNewPassword()));
		userRepository.save(user);

	}

}
