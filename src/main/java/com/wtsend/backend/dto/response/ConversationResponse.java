package com.wtsend.backend.dto.response;

import java.time.Instant;
import java.util.List;

import lombok.Builder;

@Builder
public record ConversationResponse(
    Long id,
    String type,
    List<ParticipantResponse> participants,
    GroupInfoResponse groupInfo,
    LastMessage lastMessage,
    Instant lastMessageAt) {
}