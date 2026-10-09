package com.wtsend.backend.dto.request;

import java.util.List;

import com.wtsend.backend.model.enums.ConversationType;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class CreateConversationRequest {
	@NotNull(message = "type is required")
	private ConversationType type;

	private String name;
	@NotEmpty(message = "list member can not empty")
	private List<String> memberIds;

}
