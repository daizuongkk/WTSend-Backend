package com.wtsend.backend.dto.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {
	private Long id;
	private String type;
	private List<ParticipantResponse> participants;
	private GroupInfoResponse groupInfo;
	private LastMessage lastMessage;
	private Instant lastMessageAt;
}
