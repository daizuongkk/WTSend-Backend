package com.wtsend.backend.dto.response;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter

@Setter

public class FriendRequestResponse {
	private Long id;
	private UserResponse from;
	private UserResponse to;
	private String message;
	private Instant createdAt;
	private Instant updatedAt;
}
