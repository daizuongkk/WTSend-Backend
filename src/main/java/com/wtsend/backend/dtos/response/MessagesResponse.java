package com.wtsend.backend.dtos.response;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MessagesResponse {
	private List<MessageResponse> messages;
	private Instant nextCursor;
}
