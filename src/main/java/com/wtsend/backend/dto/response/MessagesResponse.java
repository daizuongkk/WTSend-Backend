package com.wtsend.backend.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MessagesResponse {
	private List<MessageResponse> messages;
	// Opaque keyset cursor -- clients echo it back untouched.
	// See com.wtsend.backend.libs.MessageCursor.
	private String nextCursor;
}
