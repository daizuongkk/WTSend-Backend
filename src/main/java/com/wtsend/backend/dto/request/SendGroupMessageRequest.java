package com.wtsend.backend.dto.request;

import com.wtsend.backend.dto.MediaRequest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record SendGroupMessageRequest(

    @NotNull(message = "Conversation Id is required") Long conversationId,

    /**
     * Bounded by messages.content, a MySQL TEXT: 65,535 bytes, which under
     * utf8mb4 is as few as 16,383 characters. Past that the insert failed with a
     * 500 rather than a 400.
     */
    @Size(max = 16000, message = "content is too long") String content,

    MediaRequest mediaRequest

) {
}