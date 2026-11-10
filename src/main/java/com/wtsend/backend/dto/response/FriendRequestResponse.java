package com.wtsend.backend.dto.response;

import java.time.Instant;

import lombok.Builder;

@Builder
public record FriendRequestResponse(
    Long id,
    UserResponse from,
    UserResponse to,
    String message,
    Instant createdAt,
    Instant updatedAt) {
}