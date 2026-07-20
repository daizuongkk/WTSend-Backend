package com.wtsend.backend.controllers;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wtsend.backend.dto.request.GoogleAuthRequest;
import com.wtsend.backend.dto.request.SignInRequest;
import com.wtsend.backend.dto.request.SignUpRequest;
import com.wtsend.backend.dto.response.AuthResponse;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.services.interfaces.IAuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final IAuthService authService;

	private static final String REFRESH_TOKEN = "refreshToken";

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refreshToken(@CookieValue(name = REFRESH_TOKEN, required = false) String req) {
		var res = authService.refreshToken(req);
		ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN,
				res.getRefreshToken()).httpOnly(true)
				.secure(true).maxAge(Duration.ofDays(7)).path("/").sameSite("None").build();
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(res);
	}

	@PostMapping("/google")
	public ResponseEntity<AuthResponse> googleAuth(@RequestBody @Valid GoogleAuthRequest request) {
		AuthResponse res = authService.googleLogin(request.getCredential());
		ResponseCookie refreshToken = ResponseCookie.from(REFRESH_TOKEN, res.getRefreshToken()).httpOnly(true)
				.secure(true).maxAge(Duration.ofDays(7)).path("/").sameSite("None").build();

		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshToken.toString())
				.body(res);
	}

	@PostMapping("/register")
	public ResponseEntity<UserResponse> signUp(@RequestBody @Valid SignUpRequest request) {
		UserResponse res = authService.signUp(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(res);
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> signIn(@RequestBody @Valid SignInRequest request) {
		AuthResponse res = authService.signIn(request);

		if (res.getRefreshToken() != null) {
			ResponseCookie refreshToken = ResponseCookie.from(REFRESH_TOKEN, res.getRefreshToken()).httpOnly(true)
					.secure(true).maxAge(Duration.ofDays(7)).path("/").sameSite("None").build();
			return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshToken.toString())
					.body(res);
		}

		return ResponseEntity.ok(res);
	}

	@PostMapping("/logout")
	public ResponseEntity<String> signOut(
			@CookieValue(name = REFRESH_TOKEN, required = false) String refreshToken) {
		String res = authService.signOut(refreshToken);
		ResponseCookie deleteCookie = ResponseCookie.from(REFRESH_TOKEN, "")
				.httpOnly(true)
				.secure(true)
				.path("/")
				.maxAge(0).sameSite("None")
				.build();
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, deleteCookie.toString()).body(res);
	}

}
