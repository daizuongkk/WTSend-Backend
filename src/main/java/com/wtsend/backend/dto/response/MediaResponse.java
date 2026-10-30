package com.wtsend.backend.dto.response;

import java.util.UUID;

import com.wtsend.backend.model.enums.MediaStatus;

public record MediaResponse(
    UUID id, String fileName, String contentType, long size, MediaStatus status,
    Integer width, Integer height, Integer durationSec, String thumbnailUrl) {
}