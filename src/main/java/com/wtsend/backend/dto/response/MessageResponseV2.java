package com.wtsend.backend.dto.response;

import java.time.Instant;

public record MessageResponseV2(
    Long id,
    Long conversationId,
    String content,
    MediaResponse mediaResponse,
    Instant createdAt,
    Instant updatedAt,
    Sender sender

) {
}
