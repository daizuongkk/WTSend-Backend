package com.wtsend.backend.libs;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import com.wtsend.backend.dto.response.ParticipantResponse;
import com.wtsend.backend.model.Participant;
import com.wtsend.backend.model.User;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ParticipantMapper {
  private final ModelMapper mapper;

  public ParticipantResponse toResponse(Participant participant) {

    User user = participant.getUser();
    return ParticipantResponse.builder()
        .id(participant.getId())
        .userId(user.getId())
        .displayName(user.getDisplayName())
        .avatarUrl(user.getAvatarUrl())
        .lastReadAt(participant.getLastReadAt())
        .unreadCounts(participant.getUnreadCounts())
        .lastSeenMessageId(participant.getLastSeenMessage() != null ? participant.getLastSeenMessage().getId()
            : null)
        .joinedAt(participant.getJoinedAt())
        .build();
  }
}
