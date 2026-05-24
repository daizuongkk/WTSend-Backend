package com.wtsend.backend.controllers;

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

import com.wtsend.backend.dtos.request.ChangePasswordRequest;
import com.wtsend.backend.dtos.request.UpdateUserRequest;
import com.wtsend.backend.dtos.response.UserResponse;
import com.wtsend.backend.services.interfaces.IAuthService;
import com.wtsend.backend.services.interfaces.IUserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
	private final IUserService userService;
	private final IAuthService authService;

	@GetMapping("/me")
	public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok(userService.me(jwt));
	}

	@GetMapping("/search")
	public ResponseEntity<UserResponse> searchUserByUsername(@RequestParam(name = "username") String username) {
		return ResponseEntity.ok(userService.findByUsername(username));
	}

	@PostMapping("/upload")
	public ResponseEntity<String> uploadAvatar(@RequestParam(name = "img") MultipartFile file,
			@AuthenticationPrincipal Jwt jwt) {
		String imgUrl = userService.uploadAvatar(file, jwt.getSubject());

		return ResponseEntity.ok("{\"avatarUrl\":\"" + imgUrl + "\"}");
	}

	@PutMapping("/password")
	public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {

		authService.changePassword(request);
		return ResponseEntity.ok(null);
	}

	@PutMapping("")
	public ResponseEntity<UserResponse> updateUser(@ModelAttribute UpdateUserRequest request) {

		return ResponseEntity.ok(userService.updateUser(request));
	}

}
