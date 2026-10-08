package com.wtsend.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class SendDirectMessageRequest {

	@NotBlank(message = "Recipient id is required")
	private String recipientId;

	// Null on the first message to a friend: the client has no conversation yet.
	// MessageService resolves or creates it.
	private Long conversationId;

	private String content;

	/** Cloudinary URL of an attached image. */
	private String imgUrl;

	/**
	 * An image-only message carries no text, so {@code content} cannot be
	 * {@code @NotBlank} on its own -- but a message with neither is meaningless.
	 */
	@JsonIgnore
	@AssertTrue(message = "A message needs text or an image")
	public boolean isTextOrImagePresent() {
		return hasText(content) || hasText(imgUrl);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
