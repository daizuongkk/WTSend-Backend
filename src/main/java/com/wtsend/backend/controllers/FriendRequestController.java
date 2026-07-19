package com.wtsend.backend.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wtsend.backend.dto.request.AddFriendRequest;
import com.wtsend.backend.dto.response.FriendRequestResponse;
import com.wtsend.backend.services.interfaces.IFriendRequestService;

@RestController
@RequestMapping("/api/friend-requests")
public class FriendRequestController {

	private final IFriendRequestService friendRequestService;

	FriendRequestController(IFriendRequestService friendRequestService) {
		this.friendRequestService = friendRequestService;
	}

	@PostMapping("")
	public ResponseEntity<String> sendFriendRequest(@RequestBody AddFriendRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		friendRequestService.sendFriendRequest(jwt.getSubject(), request);
		return ResponseEntity.ok("send friend request successfully");
	}

	@PostMapping("/{id}/accept")
	public ResponseEntity<String> acceptFriendRequest(@PathVariable(name = "id") Long id,
			@AuthenticationPrincipal Jwt jwt) {

		friendRequestService.acceptFriendRequest(jwt.getSubject(), id);

		return ResponseEntity.ok("accept friend successfully");
	}

	@PostMapping("/{id}/reject")
	public ResponseEntity<String> rejectFriendRequest(@PathVariable(name = "id") Long id,
			@AuthenticationPrincipal Jwt jwt) {

		friendRequestService.rejectFriendRequest(jwt.getSubject(), id);
		return ResponseEntity.ok("reject friend");
	}

	@GetMapping()
	public ResponseEntity<Map<String, List<FriendRequestResponse>>> getFriendRequests(@AuthenticationPrincipal Jwt jwt) {

		return ResponseEntity.ok(friendRequestService.getFriendRequests(jwt.getSubject()));
	}
}
