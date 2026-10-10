package com.wtsend.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
/**
 * {@code @AllArgsConstructor} is what makes this deserialisable, and it is not
 * decoration: Lombok's {@code @Builder} generates an all-args constructor at
 * package level and suppresses the default one, so under Jackson 3 this class
 * had no creator at all and every request body carrying it answered 500.
 * Jackson matches the public constructor's parameters by name, which works
 * because Spring Boot compiles with {@code -parameters}.
 */
@AllArgsConstructor
@Builder
public class SendGroupMessageRequest {

	@NotNull(message = "Conversation Id is required")
	private Long conversationId;

	/**
	 * Bounded by messages.content, a MySQL TEXT: 65,535 bytes, which under
	 * utf8mb4 is as few as 16,383 characters. Past that the insert failed with a
	 * 500 rather than a 400.
	 */
	@Size(max = 16000, message = "content is too long")
	private String content;

	/** Cloudinary URL of an attached image. Named to match {@code Message.imgUrl}, and bounded by its column. */
	@Size(max = 255, message = "imgUrl must be at most 255 characters")
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
