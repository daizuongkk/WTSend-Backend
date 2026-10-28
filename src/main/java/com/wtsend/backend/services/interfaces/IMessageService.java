package com.wtsend.backend.services.interfaces;

import java.util.UUID;

import com.wtsend.backend.dto.request.SendDirectMessageRequest;
import com.wtsend.backend.dto.request.SendGroupMessageRequest;
import com.wtsend.backend.dto.response.MessageResponse;
import com.wtsend.backend.dto.response.MessagesResponse;

public interface IMessageService {
  /**
   * Returns the stored message, so the caller can reconcile an optimistic one.
   */
  MessageResponse sendDirectMessage(SendDirectMessageRequest request, UUID senderId);

  MessageResponse sendGroupMessage(SendGroupMessageRequest request, UUID senderId);

  MessagesResponse getMessages(Long conversationId, UUID userId, int limit, String cursor);
}
