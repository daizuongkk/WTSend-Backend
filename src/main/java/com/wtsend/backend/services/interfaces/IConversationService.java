package com.wtsend.backend.services.interfaces;

import java.util.List;

import org.springframework.stereotype.Service;

import com.wtsend.backend.dtos.request.CreateConversationRequest;
import com.wtsend.backend.dtos.response.ConversationResponse;

@Service
public interface IConversationService {

	public abstract List<ConversationResponse> getConversations(String userId);

	public abstract void createConversation(CreateConversationRequest request, String userId);

	public abstract List<Long> getConversationIdsByUserId(String userId);

	public abstract void markAsSeen(Long conversationId, String userId);

}
