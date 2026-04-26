package com.wtsend.backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SendGroupMessageRequest {

	@NotNull(message = "Conversation Id is required")
	private Long conversationId;

	@NotBlank(message = "Message is required")
	private String content;
	private String avatarUrl;

}
