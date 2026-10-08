package com.wtsend.backend.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * UserService.updateUser reads this as a partial update, but every field was
 * @NotBlank/@NotEmpty, so validation rejected any call that left one out --
 * changing a bio meant resending username, email and display name too.
 */
class UpdateUserRequestTest {

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

	private static Set<ConstraintViolation<UpdateUserRequest>> validate(Consumer<UpdateUserRequest> customiser) {
		UpdateUserRequest request = new UpdateUserRequest();
		customiser.accept(request);
		return validator.validate(request);
	}

	@Test
	void anEmptyRequestIsValid() {
		// The caller changed nothing. Every field being @NotBlank made this four
		// violations, which is what blocked partial updates.
		assertThat(validate(request -> {
		})).isEmpty();
	}

	@Test
	void changingOnlyTheBioIsValid() {
		assertThat(validate(request -> request.setBio("just the bio"))).isEmpty();
	}

	@Test
	void aBlankDisplayNameIsRejected() {
		// Not clearable: the column is NOT NULL, and the service would have stored
		// the blank once @NotEmpty was gone.
		assertThat(validate(request -> request.setDisplayName("   ")))
				.extracting(violation -> violation.getPropertyPath().toString())
				.containsExactly("displayName");
	}

	@Test
	void thereIsNoEmailFieldToUpdate() {
		// Changing an address reset emailVerified, which gave "unverified" two
		// meanings and would have put established accounts in scope for the
		// 24-hour deletion of abandoned registrations. It also let anyone move
		// their account onto an address they do not own.
		assertThat(Arrays.stream(UpdateUserRequest.class.getDeclaredFields())
				.map(Field::getName))
				.doesNotContain("email");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "25/12/1998" })
	void aBirthdayCanBeSetOrCleared(String birthday) {
		// Empty clears it, matching phone. It previously had no empty branch, so a
		// birthday could be set but never removed.
		assertThat(validate(request -> request.setBirthday(birthday))).isEmpty();
	}

	@Test
	void aMalformedBirthdayIsStillRejected() {
		assertThat(validate(request -> request.setBirthday("1998-12-25"))).isNotEmpty();
	}

	@Test
	void aTooShortUsernameIsStillRejected() {
		assertThat(validate(request -> request.setUsername("short")))
				.extracting(violation -> violation.getPropertyPath().toString())
				.containsExactly("username");
	}
}
