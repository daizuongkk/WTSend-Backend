package com.wtsend.backend.services;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.wtsend.backend.dtos.RefreshToken;
import com.wtsend.backend.dtos.TokenPayload;
import com.wtsend.backend.dtos.request.SignInRequest;
import com.wtsend.backend.dtos.request.SignUpRequest;
import com.wtsend.backend.dtos.response.AuthResponse;
import com.wtsend.backend.dtos.response.UserResponse;
import com.wtsend.backend.exceptions.ResourceNotFoundException;
import com.wtsend.backend.models.User;
import com.wtsend.backend.repositories.RefreshTokenRepository;
import com.wtsend.backend.services.interfaces.IAuthService;

@Service
public class AuthService implements IAuthService {
	private final AuthenticationManager authenticationManager;

	private final JwtService jwtService;

	private final UserService userService;

	private final RefreshTokenService refreshTokenService;

	private final RefreshTokenRepository refreshTokenRepo;

	AuthService(AuthenticationManager authenticationManager, JwtService jwtService, UserService userService,
			RefreshTokenService refreshTokenService, RefreshTokenRepository refreshTokenRepo) {
		this.authenticationManager = authenticationManager;
		this.jwtService = jwtService;
		this.userService = userService;
		this.refreshTokenService = refreshTokenService;
		this.refreshTokenRepo = refreshTokenRepo;
	}

	public AuthResponse signIn(SignInRequest req) {
		UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
				req.getUsername().trim(), req.getPassword());

		Authentication auth = authenticationManager.authenticate(usernamePasswordAuthenticationToken);

		User user = (User) auth.getPrincipal();

		RefreshToken newRfToken = refreshTokenService.create(user);
		return AuthResponse.builder().message("Login successfully!").success(true).username(req.getUsername())
				.email(user.getEmail()).displayName(user.getDisplayName())
				.accessToken(jwtService.generateToken(user)).refreshToken(newRfToken.getToken()).build();
	}

	public AuthResponse signOut(String token) {
		TokenPayload payload = jwtService.parseToken(token.replace("Bearer ", "").trim());

		String userId = payload.getSub();

		RefreshToken deletedToken = refreshTokenRepo.findByUserId(userId)
				.orElseThrow(() -> new ResourceNotFoundException("Not found user by userId: " + userId));

		refreshTokenRepo.delete(deletedToken);
		return AuthResponse.builder().success(true).message("Signed out successfully").build();
	}

	public AuthResponse signUp(SignUpRequest req) {
		UserResponse newUser = userService.createUser(req);
		return AuthResponse.builder().message("Registered successfully").success(true).username(newUser.getUsername())
				.displayName(newUser.getDisplayName()).email(newUser.getEmail()).build();
	}

	public AuthResponse refreshToken(String token) {

		User user = refreshTokenService.verify(token);
		RefreshToken newRfToken = refreshTokenService.create(user);

		refreshTokenRepo.deleteByUserId(user.getId());
		String newAccessToken = jwtService.generateToken(user);
		return AuthResponse.builder().success(true).message("Refresh token successfully")
				.refreshToken(newRfToken.getToken())
				.accessToken(newAccessToken)
				.build();
	}
}
