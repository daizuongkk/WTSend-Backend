package com.wtsend.backend.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wtsend.backend.dto.response.FriendResponse;
import com.wtsend.backend.services.interfaces.IFriendService;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

	private final IFriendService friendService;

	FriendController(IFriendService friendService) {
		this.friendService = friendService;
	}

	@GetMapping()
	public ResponseEntity<List<FriendResponse>> getAllFriends(@AuthenticationPrincipal Jwt jwt) {

		return ResponseEntity.ok(friendService.getAllFriends(jwt.getSubject()));
	}

}
