package com.wtsend.backend.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * email was only @NotBlank here while SignUpRequest had @NotBlank and @Email,
 * so the two halves of the same flow disagreed on what an address is.
 */
class SignInRequestTest {

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

	@Test
	void acceptsAWellFormedAddress() {
		assertThat(validator.validate(SignInRequest.builder()
				.email("someone@example.com").password("secret").build())).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "not-an-email", "@example.com", "two@@at.com", " " })
	void rejectsAnythingThatIsNotAnAddress(String email) {
		assertThat(validator.validate(SignInRequest.builder()
				.email(email).password("secret").build())).isNotEmpty();
	}

	@Test
	void aDomainWithoutADotIsAccepted() {
		// Worth stating rather than discovering: Jakarta's @Email is lenient and
		// treats "someone@localhost" as valid. It checks the shape, not that the
		// domain could receive mail -- only sending to it proves that, which is
		// what the verification link is for.
		assertThat(validator.validate(SignInRequest.builder()
				.email("someone@localhost").password("secret").build())).isEmpty();
	}

	@Test
	void stillRequiresAPassword() {
		assertThat(validator.validate(SignInRequest.builder()
				.email("someone@example.com").password("").build()))
				.extracting(violation -> violation.getPropertyPath().toString())
				.containsExactly("password");
	}
}
