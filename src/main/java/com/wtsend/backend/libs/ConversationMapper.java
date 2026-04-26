package com.wtsend.backend.libs;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import com.wtsend.backend.dtos.response.ConversationResponse;
import com.wtsend.backend.dtos.response.GroupInfoResponse;
import com.wtsend.backend.dtos.response.LastMessage;
import com.wtsend.backend.models.Conversation;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class ConversationMapper {

	private final ModelMapper mapper;
	private final ParticipantMapper participantMapper;
	private final MessageMapper messageMapper;

	public ConversationResponse toResponse(Conversation conversation) {
		ConversationResponse response = mapper.map(conversation, ConversationResponse.class);

		response.setLastMessage(mapper.map(conversation.getLastMessage(), LastMessage.class));
		response.setParticipants(conversation.getParticipants().stream().map(participantMapper::toResponse).toList());
		response.setGroupInfo(conversation.getGroup() == null ? null
				: GroupInfoResponse.builder()
						.name(conversation.getGroup().getName())
						.avatarUrl(conversation.getGroup().getAvatarUrl())
						.creatorId(conversation.getGroup().getCreator().getId())
						.build());
		response.setLastMessage(messageMapper.toLastMessage(conversation.getLastMessage()));

		return response;
	}
}
