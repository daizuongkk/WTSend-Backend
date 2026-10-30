package com.wtsend.backend.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record InitUploadResponse(
    UUID mediaId, String uploadUrl, String method,
    Map<String, String> headers, Instant expiresAt) {
}