package com.wtsend.backend.controllers;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wtsend.backend.dto.request.ForgotPasswordRequest;
import com.wtsend.backend.dto.request.GoogleAuthRequest;
import com.wtsend.backend.dto.request.ResetPasswordRequest;
import com.wtsend.backend.dto.request.SignInRequest;
import com.wtsend.backend.dto.request.SignUpRequest;
import com.wtsend.backend.dto.response.AuthResponse;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.libs.RefreshTokenCookie;
import com.wtsend.backend.services.interfaces.IAuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final IAuthService authService;

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refreshToken(@CookieValue(name = RefreshTokenCookie.NAME, required = false) String req) {
		var res = authService.refreshToken(req);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.of(res.getRefreshToken()).toString())
				.body(res);
	}

	@PostMapping("/google")
	public ResponseEntity<AuthResponse> googleAuth(@RequestBody @Valid GoogleAuthRequest request) {
		AuthResponse res = authService.googleLogin(request.getCode());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.of(res.getRefreshToken()).toString())
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
			return ResponseEntity.ok()
					.header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.of(res.getRefreshToken()).toString())
					.body(res);
		}

		return ResponseEntity.ok(res);
	}

	/**
	 * Always 200 for an address with no account, so this cannot be used to
	 * discover which addresses are registered. An unverified account is the one
	 * exception and reports EMAIL_NOT_VERIFIED -- it is sent a verification link
	 * instead, since resetting through an unproven address would defeat the
	 * verification flow.
	 */
	@PostMapping("/forgot-password")
	public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
		authService.forgotPassword(request);
		return ResponseEntity.ok(Map.of("message",
				"Nếu địa chỉ này có tài khoản, chúng tôi đã gửi email đặt lại mật khẩu."));
	}

	/**
	 * No tokens in the response on purpose: the reset revokes every session, so
	 * handing back a fresh one would contradict that, and signing in with the new
	 * password confirms it is what the user meant to set.
	 */
	@PostMapping("/reset-password")
	public ResponseEntity<Map<String, String>> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
		authService.resetPassword(request);
		return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công, hãy đăng nhập lại."));
	}

	@PostMapping("/logout")
	public ResponseEntity<String> signOut(
			@CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
		String res = authService.signOut(refreshToken);

		// Revoking in Redis is not enough: without an expiring Set-Cookie the browser
		// keeps sending the dead cookie on every request.
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.expired().toString())
				.body(res);
	}

}
