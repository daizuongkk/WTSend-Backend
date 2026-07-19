package com.wtsend.backend.libs;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import com.wtsend.backend.dto.response.ParticipantResponse;
import com.wtsend.backend.model.Participant;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ParticipantMapper {
	private final ModelMapper mapper;

	public ParticipantResponse toResponse(Participant participant) {
		ParticipantResponse response = mapper.map(participant, ParticipantResponse.class);

		response.setUserId(participant.getUser().getId());
		response.setDisplayName(participant.getUser().getDisplayName());
		response.setAvatarUrl(participant.getUser().getAvatarUrl());

		response.setLastSeenMessageId(
				participant.getLastSeenMessage() != null ? participant.getLastSeenMessage().getId() : null);
		return response;
	}
}
