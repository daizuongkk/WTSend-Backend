package com.wtsend.backend.controllers;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wtsend.backend.dto.response.AuthResponse;
import com.wtsend.backend.libs.RefreshTokenCookie;
import com.wtsend.backend.services.interfaces.IEmailService;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
public class EmailController {
	private final IEmailService emailService;

	@PostMapping("/send-verify-email")
	public ResponseEntity<Map<String, String>> resendVerifyEmail(@AuthenticationPrincipal Jwt jwt) {

		emailService.sendVerifyLink(jwt.getSubject());
		return ResponseEntity.ok(Map.of("message", "resend verification email successfully"));
	}

	@PostMapping("/verify-email")
	public ResponseEntity<AuthResponse> verifyEmail(@RequestParam(name = "token") @NotBlank(message = "Verification token is required") String token) {

		AuthResponse response = emailService.verifyEmail(token);
		// Through RefreshTokenCookie, not hand-rolled. This was the one call site
		// that would not follow a change to that class, and the attributes have to
		// match for RefreshTokenCookie.expired() to be able to delete the cookie
		// again -- attribute drift between a set and a later delete is a bug this
		// codebase has already had once.
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.of(response.getRefreshToken()).toString())
				.body(response);
	}
}
