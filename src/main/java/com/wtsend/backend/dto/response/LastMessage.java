package com.wtsend.backend.dto.response;

import java.time.Instant;

import lombok.Builder;

@Builder
public record LastMessage(
    Long id,
    String content,
    MediaResponse media,
    Instant createdAt,
    Instant updatedAt,
    Sender sender) {
}