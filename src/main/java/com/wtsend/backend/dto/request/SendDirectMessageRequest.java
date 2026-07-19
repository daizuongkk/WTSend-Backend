package com.wtsend.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class SendDirectMessageRequest {

	@NotBlank(message = "Recipient id is required")
	private String recipientId;

	@NotNull(message = "conversation id is required")
	private Long conversationId;

	private String imgUrl;
	@NotBlank(message = "content id is required")
	private String content;
}
