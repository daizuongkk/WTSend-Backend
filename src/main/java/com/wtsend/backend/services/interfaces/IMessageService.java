package com.wtsend.backend.services.interfaces;

import com.wtsend.backend.dto.request.SendDirectMessageRequest;
import com.wtsend.backend.dto.request.SendGroupMessageRequest;
import com.wtsend.backend.dto.response.MessageResponse;
import com.wtsend.backend.dto.response.MessagesResponse;

public interface IMessageService {
	/** Returns the stored message, so the caller can reconcile an optimistic one. */
	MessageResponse sendDirectMessage(SendDirectMessageRequest request, String senderId);

	MessageResponse sendGroupMessage(SendGroupMessageRequest request, String senderId);

	MessagesResponse getMessages(Long conversationId, String userId, int limit, String cursor);
}
