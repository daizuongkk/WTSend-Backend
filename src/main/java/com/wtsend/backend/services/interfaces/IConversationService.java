package com.wtsend.backend.services.interfaces;

import java.util.List;

import com.wtsend.backend.dto.request.CreateConversationRequest;
import com.wtsend.backend.dto.response.ConversationResponse;

public interface IConversationService {

	List<ConversationResponse> getConversations(String userId);

	ConversationResponse createConversation(CreateConversationRequest request, String userId);

	List<Long> getConversationIdsByUserId(String userId);

	void markAsSeen(Long conversationId, String userId);

}
