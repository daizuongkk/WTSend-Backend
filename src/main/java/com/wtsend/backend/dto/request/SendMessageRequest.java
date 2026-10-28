package com.wtsend.backend.dto.request;

import com.wtsend.backend.dto.MediaRequest;
import com.wtsend.backend.dto.MessageType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendMessageRequest(

    String tempId,

    @NotBlank(message = "Conversation id is required") String conversationId,
    String content,

    @NotNull(message = "Message type is required") MessageType messageType,

    MediaRequest messageMedia

) {
}
