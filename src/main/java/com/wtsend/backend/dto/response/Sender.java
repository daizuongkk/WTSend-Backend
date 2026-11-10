package com.wtsend.backend.dto.response;

import lombok.Builder;

@Builder
public record Sender(
    String id,
    String displayName,
    String avatarUrl) {
}