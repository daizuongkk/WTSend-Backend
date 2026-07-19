package com.wtsend.backend.controllers;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wtsend.backend.dto.response.AuthResponse;
import com.wtsend.backend.models.User;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.interfaces.IEmailService;
import com.wtsend.backend.services.interfaces.IUserService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EmailController {
	private final IEmailService emailService;
	private final UserRepository userRepo;

	@PostMapping("/send-verify-email")
	public ResponseEntity<String> resendVerifyEmail(@AuthenticationPrincipal Jwt jwt) {

		User user = userRepo.findById(jwt.getSubject())
				.orElseThrow(() -> new EntityNotFoundException("User not found by id: " + jwt.getSubject()));
		emailService.sendVerifyLink(user);
		return ResponseEntity.ok("{\"message:\" \"resend verification email successfully\"}");
	}

	@PostMapping("/verify-email")
	public ResponseEntity<AuthResponse> verifyEmail(@RequestParam(name = "token") String token) {

		AuthResponse response = emailService.verifyEmail(token);
		ResponseCookie refreshToken = ResponseCookie.from("refreshToken", response.getRefreshToken()).httpOnly(true)
				.secure(true).maxAge(Duration.ofDays(7)).path("/").sameSite("None").build();
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshToken.toString())
				.body(response);
	}
}
