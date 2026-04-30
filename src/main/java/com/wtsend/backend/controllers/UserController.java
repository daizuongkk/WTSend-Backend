package com.wtsend.backend.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wtsend.backend.dtos.response.UserResponse;
import com.wtsend.backend.services.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {
	private final UserService userService;

	UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/me")
	public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {

		return ResponseEntity.ok(userService.me(jwt));
	}

	@GetMapping("/search")
	public ResponseEntity<UserResponse> findByUsername(@RequestParam(name = "username") String username) {
		return ResponseEntity.ok(userService.findByUsername(username));
	}

}
