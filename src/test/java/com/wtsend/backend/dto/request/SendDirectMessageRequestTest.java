package com.wtsend.backend.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Bean validation runs before the controller, so a constraint here can make a
 * service branch unreachable without any test in {@code MessageServiceTest}
 * noticing -- those call the service directly and never see the validator.
 */
class SendDirectMessageRequestTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void openValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	private static SendDirectMessageRequest request(String recipientId, Long conversationId, String content) {
		SendDirectMessageRequest r = new SendDirectMessageRequest();
		ReflectionTestUtils.setField(r, "recipientId", recipientId);
		ReflectionTestUtils.setField(r, "conversationId", conversationId);
		ReflectionTestUtils.setField(r, "content", content);
		return r;
	}

	@Test
	void aNullConversationIdIsValid() {
		// The first message to a friend carries no conversation id. @NotNull here
		// rejected it with 400, so that conversation could never be started at all.
		Set<ConstraintViolation<SendDirectMessageRequest>> violations = validator
				.validate(request("bob-id", null, "hi"));

		assertThat(violations).isEmpty();
	}

	@Test
	void aRecipientIdIsStillRequired() {
		assertThat(validator.validate(request("  ", null, "hi")))
				.extracting(v -> v.getPropertyPath().toString())
				.containsExactly("recipientId");
	}
}
