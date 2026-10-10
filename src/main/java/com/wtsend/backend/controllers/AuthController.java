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

import com.wtsend.backend.dto.request.FacebookAuthRequest;
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
		return withRefreshCookie(authService.refreshToken(req));
	}

	/**
	 * Answers 200 and attaches the refresh cookie only when there is a token to
	 * attach.
	 *
	 * <p>
	 * An unverified account gets an access token and no refresh token, so this
	 * guard is not cosmetic: {@code ResponseCookie.from(name, null)} rejects the
	 * null value, which turned a perfectly ordinary provider sign-in by an
	 * unverified account into a 500. /login had the guard and the other three
	 * endpoints did not.
	 */
	private static ResponseEntity<AuthResponse> withRefreshCookie(AuthResponse res) {
		if (res.getRefreshToken() == null)
			return ResponseEntity.ok(res);

		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.of(res.getRefreshToken()).toString())
				.body(res);
	}

	@PostMapping("/google")
	public ResponseEntity<AuthResponse> googleAuth(@RequestBody @Valid GoogleAuthRequest request) {
		return withRefreshCookie(authService.googleLogin(request.getCode()));
	}

	/**
	 * Mirrors the Google endpoint rather than going through
	 * {@code .oauth2Login()}: one OAuth mechanism in the app is enough, and the
	 * redirect flow would need its own success handler, session handling and
	 * callback URLs for no gain here.
	 */
	@PostMapping("/facebook")
	public ResponseEntity<AuthResponse> facebookAuth(@RequestBody @Valid FacebookAuthRequest request) {
		return withRefreshCookie(authService.facebookLogin(request));
	}

	@PostMapping("/register")
	public ResponseEntity<UserResponse> signUp(@RequestBody @Valid SignUpRequest request) {
		UserResponse res = authService.signUp(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(res);
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> signIn(@RequestBody @Valid SignInRequest request) {
		return withRefreshCookie(authService.signIn(request));
	}

	/**
	 * Always this 200, with this body, for every address: registered, verified,
	 * unverified, or unknown. No exceptions -- an unverified account is sent a
	 * verification link instead of a reset link, but is not told so, because
	 * saying so was enough to tell anyone which addresses are registered.
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
