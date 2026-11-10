package com.wtsend.backend.dto.response;

import java.time.Instant;

import lombok.Builder;

@Builder
public record ParticipantResponse(
    Long id,
    String userId,
    String displayName,
    String avatarUrl,
    Instant lastReadAt,
    Long unreadCounts,
    Long lastSeenMessageId,
    Instant joinedAt) {
}