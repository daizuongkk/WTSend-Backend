package com.wtsend.backend.libs;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import com.wtsend.backend.dtos.response.LastMessage;
import com.wtsend.backend.dtos.response.MessageResponse;
import com.wtsend.backend.dtos.response.Sender;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.models.Message;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class MessageMapper {

	private final ModelMapper mapper;
	private final UserUtils userUtils;

	public LastMessage toLastMessage(Message message) {
		return LastMessage.builder().id(message.getId()).content(message.getContent())
				.createdAt(message.getCreatedAt()).updatedAt(message.getUpdatedAt())
				.sender(Sender.builder().id(message.getSender().getId())
						.avatarUrl(message.getSender().getAvatarUrl())
						.displayName(message.getSender().getDisplayName()).build())
				.build();
	}

	public MessageResponse toResponse(Message message) {
		MessageResponse response = mapper.map(message, MessageResponse.class);
		response.setSender(userUtils.toSender(message.getSender()));
		response.setConversationId(message.getConversation().getId());
		return response;
	}

}
