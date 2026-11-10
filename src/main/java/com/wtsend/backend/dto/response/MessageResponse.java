package com.wtsend.backend.dto.response;

import java.time.Instant;

import lombok.Builder;

@Builder
public record MessageResponse(
    Long id,
    Sender sender,
    Long conversationId,
    String content,
    MediaResponse media,
    Instant createdAt,
    Instant updatedAt) {
}