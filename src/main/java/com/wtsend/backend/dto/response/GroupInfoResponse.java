package com.wtsend.backend.dto.response;

import lombok.Builder;

@Builder
public record GroupInfoResponse(
    String name,
    String creatorId,
    String avatarUrl) {
}