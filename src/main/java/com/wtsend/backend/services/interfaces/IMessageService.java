package com.wtsend.backend.services.interfaces;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.wtsend.backend.dto.request.SendDirectMessageRequest;
import com.wtsend.backend.dto.request.SendGroupMessageRequest;
import com.wtsend.backend.dto.response.MessagesResponse;

@Service
public interface IMessageService {
	public abstract void sendDirectMessage(SendDirectMessageRequest requset, String senderId);

	public abstract void sendGroupMessage(SendGroupMessageRequest request, String senderId);

	public abstract MessagesResponse getMessages(Long conversationId, int limit, Instant cursor);
}
