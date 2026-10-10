package com.wtsend.backend.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.wtsend.backend.model.enums.ConversationType;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * What {@code POST /api/conversations} accepts.
 *
 * <p>
 * {@code @NotEmpty} on the list says the list is not empty and nothing about
 * what is in it, so every one of these bodies used to pass validation and reach
 * the service.
 */
class CreateConversationRequestTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void startValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	private static CreateConversationRequest request(ConversationType type, String name, List<String> memberIds) {
		return CreateConversationRequest.builder().type(type).name(name).memberIds(memberIds).build();
	}

	@Test
	void anOrdinaryGroupIsAccepted() {
		assertThat(validator.validate(request(ConversationType.GROUP, "Nhóm bạn", List.of("user-1", "user-2"))))
				.isEmpty();
	}

	@Test
	void aNullMemberIdIsRejected() {
		// Reached participantId.equals(...) in createDirectConversation as an NPE,
		// i.e. a 500 for a request that was simply malformed.
		assertThat(validator.validate(request(ConversationType.DIRECT, null, Collections.singletonList(null))))
				.hasSize(1);
	}

	@Test
	void aBlankMemberIdIsRejected() {
		// Looked nobody up and silently produced a group one member short.
		assertThat(validator.validate(request(ConversationType.GROUP, "Nhóm", List.of("user-1", "  "))))
				.hasSize(1);
	}

	@Test
	void anOverLongNameIsRejectedRatherThanTruncatedByTheDatabase() {
		assertThat(validator.validate(request(ConversationType.GROUP, "x".repeat(256), List.of("user-1"))))
				.hasSize(1);
		assertThat(validator.validate(request(ConversationType.GROUP, "x".repeat(255), List.of("user-1"))))
				.isEmpty();
	}

	@Test
	void anEmptyMemberListIsStillRejected() {
		assertThat(validator.validate(request(ConversationType.GROUP, "Nhóm", List.of()))).hasSize(1);
	}
}
