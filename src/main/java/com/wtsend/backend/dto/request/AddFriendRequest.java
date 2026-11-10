package com.wtsend.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddFriendRequest(

    @NotBlank(message = "Recipient Id is required") String to,

    /**
     * Bounded by friend_request.message.
     */
    @Size(max = 255, message = "message must be at most 255 characters") String message

) {
}