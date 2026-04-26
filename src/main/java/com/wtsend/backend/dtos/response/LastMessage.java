package com.wtsend.backend.dtos.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LastMessage {
	private Long id;
	private String content;
	private Instant createdAt;
	private Instant updatedAt;
	private Sender sender;

}
