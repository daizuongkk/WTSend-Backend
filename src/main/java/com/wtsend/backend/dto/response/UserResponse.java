package com.wtsend.backend.dto.response;

import java.time.Instant;

import lombok.Builder;

@Builder
public record UserResponse(
    String id,
    String username,
    String displayName,
    String birthday,
    String email,
    Boolean emailVerified,
    String phone,
    boolean phoneVerified,
    boolean showOnlineStatus,
    String avatarUrl,
    String avatarId,
    String bio,
    Instant createdAt,
    Instant updatedAt) {
}