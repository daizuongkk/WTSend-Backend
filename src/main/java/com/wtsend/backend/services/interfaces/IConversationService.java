package com.wtsend.backend.services.interfaces;

import java.util.List;
import java.util.UUID;

import com.wtsend.backend.dto.request.CreateConversationRequest;
import com.wtsend.backend.dto.response.ConversationResponse;

public interface IConversationService {

  List<ConversationResponse> getConversations(UUID userId);

  ConversationResponse createConversation(CreateConversationRequest request, UUID userId);

  List<Long> getConversationIdsByUserId(UUID userId);

  void markAsSeen(Long conversationId, UUID userId);

}
