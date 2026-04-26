package com.wtsend.backend.services.interfaces;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.wtsend.backend.dtos.request.SendDirectMessageRequset;
import com.wtsend.backend.dtos.request.SendGroupMessageRequest;
import com.wtsend.backend.dtos.response.MessagesResponse;

@Service
public interface IMessageService {
	public abstract void sendDirectMessage(SendDirectMessageRequset requset, String senderId);

	public abstract void sendGroupMessage(SendGroupMessageRequest request, String senderId);

	public abstract MessagesResponse getMessages(Long conversationId, int limit, Instant cursor);
}
