package com.wtsend.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class SendDirectMessageRequest {

	@NotBlank(message = "Recipient id is required")
	private String recipientId;

	// Null on the first message to a friend: the client has no conversation yet.
	// MessageService resolves or creates it.
	private Long conversationId;

	private String imgUrl;
	@NotBlank(message = "content id is required")
	private String content;
}
