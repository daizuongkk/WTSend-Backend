package com.wtsend.backend.libs.utils;

import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.model.Conversation;
import com.wtsend.backend.model.User;

public class MessageHelper {
	private MessageHelper() {
	}

	public static void checkMembership(Conversation conversation, User user) {

		if (conversation.getParticipants().stream().noneMatch(p -> p.getUser().getId().equals(user.getId())))
			throw new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED)
					.withDetail("userId=" + user.getId() + " conversationId=" + conversation.getId());

	}
}
