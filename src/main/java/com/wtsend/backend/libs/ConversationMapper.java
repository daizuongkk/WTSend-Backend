package com.wtsend.backend.libs;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import com.wtsend.backend.dto.response.ConversationResponse;
import com.wtsend.backend.dto.response.GroupInfoResponse;
import com.wtsend.backend.model.Conversation;
import com.wtsend.backend.model.GroupInfo;
import com.wtsend.backend.model.Message;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class ConversationMapper {

  private final ParticipantMapper participantMapper;
  private final MessageMapper messageMapper;

  public ConversationResponse toResponse(Conversation conversation) {

    GroupInfo groupInfo = conversation.getGroup();
    Message lastMessage = conversation.getLastMessage();

    return ConversationResponse.builder()
        .id(conversation.getId())
        .type(conversation.getType().toString())
        .groupInfo(conversation.getGroup() == null ? null
            : GroupInfoResponse.builder()
                .name(groupInfo.getName())
                .avatarUrl(groupInfo.getAvatarUrl())
                .creatorId(groupInfo.getCreator().getId())
                .build())

        .lastMessage(messageMapper.toLastMessage(lastMessage))
        .lastMessageAt(conversation.getLastMessageAt())
        .participants(conversation.getParticipants().stream().map(participantMapper::toResponse)
            .toList())
        .build();
  }
}
