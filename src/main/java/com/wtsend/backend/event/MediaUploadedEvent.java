package com.wtsend.backend.event;

import java.util.UUID;

/**
 * MediaUploadedEvent
 */
public record MediaUploadedEvent(UUID mediaId) {
}
