package com.wtsend.backend.dtos.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
	private String id;
	private String username;

	private String displayName;

	private String birthday;

	private String email;
	private boolean emailVerified;

	private String phone;
	private boolean phoneVerified;
	private String avatarUrl;

	private String avatarId;

	private String bio;

	private Instant createdAt;
	private Instant updatedAt;
}
