package com.wtsend.backend.dtos.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {
	private Long id;
	private Sender sender;
	private Long conversationId;
	private String content;
	private Instant createdAt;
	private Instant updatedAt;

}
