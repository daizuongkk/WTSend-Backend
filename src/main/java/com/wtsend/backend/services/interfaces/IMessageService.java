package com.wtsend.backend.services.interfaces;

import com.wtsend.backend.dto.request.SendDirectMessageRequest;
import com.wtsend.backend.dto.request.SendGroupMessageRequest;
import com.wtsend.backend.dto.response.MessagesResponse;

public interface IMessageService {
	void sendDirectMessage(SendDirectMessageRequest request, String senderId);

	void sendGroupMessage(SendGroupMessageRequest request, String senderId);

	MessagesResponse getMessages(Long conversationId, String userId, int limit, String cursor);
}
