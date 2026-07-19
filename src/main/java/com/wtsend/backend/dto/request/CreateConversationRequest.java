package com.wtsend.backend.dto.request;

import java.util.List;

import com.wtsend.backend.model.enums.ConversationType;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreateConversationRequest {
	@NotNull(message = "type is required")
	private ConversationType type;

	private String name;
	@NotEmpty(message = "list member can not empty")
	private List<String> memberIds;

}
