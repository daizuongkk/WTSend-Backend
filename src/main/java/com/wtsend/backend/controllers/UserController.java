package com.wtsend.backend.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.wtsend.backend.dto.request.ChangePasswordRequest;
import com.wtsend.backend.dto.request.UpdateUserRequest;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.services.interfaces.IAuthService;
import com.wtsend.backend.services.interfaces.IUserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
	private final IUserService userService;
	private final IAuthService authService;

	@GetMapping("/me")
	public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(userService.me(jwt.getSubject()));
	}

	@GetMapping("/search")
	public ResponseEntity<UserResponse> searchUserByUsername(@RequestParam(name = "username") String username) {
		return ResponseEntity.ok(userService.findByUsername(username));
	}

	@PostMapping("/upload")
	public ResponseEntity<Map<String, String>> uploadAvatar(@RequestParam(name = "img") MultipartFile file,
			@AuthenticationPrincipal Jwt jwt) {
		String imgUrl = userService.uploadAvatar(file, jwt.getSubject());

		return ResponseEntity.ok(Map.of("avatarUrl", imgUrl));
	}

	@PutMapping("/password")
	public ResponseEntity<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		authService.changePassword(request, jwt.getSubject());
		return ResponseEntity.noContent().build();
	}

	@PutMapping("")
	public ResponseEntity<UserResponse> updateUser(@ModelAttribute @Valid UpdateUserRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return ResponseEntity.ok(userService.updateUser(request, jwt.getSubject()));
	}

}
