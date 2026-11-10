package com.wtsend.backend.dto.response;

import lombok.Builder;

@Builder
public record FriendResponse(
    String id,
    String username,
    String displayName,
    String avatarUrl) {
}