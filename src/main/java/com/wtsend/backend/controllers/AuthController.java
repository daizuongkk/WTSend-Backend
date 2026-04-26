package com.wtsend.backend.controllers;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wtsend.backend.dtos.request.SignInRequest;
import com.wtsend.backend.dtos.request.SignUpRequest;
import com.wtsend.backend.dtos.response.AuthResponse;
import com.wtsend.backend.services.interfaces.IAuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final IAuthService authService;

	private static final String REFRESH_TOKEN = "refreshToken";

	AuthController(IAuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/test")

	public ResponseEntity<String> test() {
		return ResponseEntity.ok("Test successful");
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refreshToken(@CookieValue(name = REFRESH_TOKEN, required = false) String req) {
		var res = authService.refreshToken(req);
		ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN,
				res.getRefreshToken()).httpOnly(true)
				.secure(true).maxAge(Duration.ofDays(7)).path("/").sameSite("None").build();
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(res);
	}

	@PostMapping("/sign-up")
	public ResponseEntity<AuthResponse> signUp(@RequestBody @Valid SignUpRequest request) {
		AuthResponse res = authService.signUp(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(res);
	}

	@PostMapping("/sign-in")
	public ResponseEntity<AuthResponse> signIn(@RequestBody @Valid SignInRequest request) {
		AuthResponse res = authService.signIn(request);
		ResponseCookie refreshToken = ResponseCookie.from(REFRESH_TOKEN, res.getRefreshToken()).httpOnly(true)
				.secure(true).maxAge(Duration.ofDays(7)).path("/").sameSite("None").build();

		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshToken.toString())
				.body(res);
	}

	@PostMapping("/sign-out")
	public ResponseEntity<AuthResponse> signOut(@RequestHeader(name = "Authorization", required = false) String token) {
		AuthResponse res = authService.signOut(token);
		ResponseCookie deleteCookie = ResponseCookie.from(REFRESH_TOKEN, "")
				.httpOnly(true)
				.secure(true)
				.path("/")
				.maxAge(0).sameSite("None")
				.build();
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, deleteCookie.toString()).body(res);
	}

}
