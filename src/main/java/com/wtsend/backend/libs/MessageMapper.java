package com.wtsend.backend.libs;

import org.springframework.stereotype.Component;

import com.wtsend.backend.dto.response.LastMessage;
import com.wtsend.backend.dto.response.MessageResponse;
import com.wtsend.backend.dto.response.Sender;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.model.Message;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class MessageMapper {

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

    return MessageResponse.builder()
        .id(message.getId())
        .content(message.getContent())
        .sender(userUtils.toSender(message
            .getSender()))
        .conversationId(message.getConversation().getId())

        .media(null)
        .createdAt(null)
        .updatedAt(null)
        .build();
  }

}
