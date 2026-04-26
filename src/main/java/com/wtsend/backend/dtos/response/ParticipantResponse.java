package com.wtsend.backend.dtos.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Builder
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ParticipantResponse {
	private Long id;
	private String userId;
	private String displayName;
	private String avatarUrl;
	private Instant lastReadAt;
	private Long unreadCounts;
	private Long lastSeenMessageId;
	private Instant joinedAt;
}
