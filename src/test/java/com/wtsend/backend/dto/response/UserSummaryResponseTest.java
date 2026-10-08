package com.wtsend.backend.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * User search returned the whole {@link UserResponse}, so any verified account
 * could read another user's email, phone, birthday and verification flags just
 * by guessing a username.
 *
 * <p>
 * This asserts on the field list rather than on one endpoint's output, because
 * the leak came from reusing a wide DTO -- and the way it comes back is
 * somebody adding a field here because it happened to be convenient.
 */
class UserSummaryResponseTest {

	private static List<String> declaredFieldNames() {
		return Arrays.stream(UserSummaryResponse.class.getDeclaredFields())
				.filter(field -> !field.isSynthetic())
				.map(Field::getName)
				.toList();
	}

	@Test
	void exposesOnlyWhatIsNeededToRecogniseSomeone() {
		assertThat(declaredFieldNames())
				.containsExactlyInAnyOrder("id", "username", "displayName", "avatarUrl");
	}

	@Test
	void carriesNoneOfThePrivateProfileFields() {
		assertThat(declaredFieldNames())
				.doesNotContainAnyElementsOf(List.of(
						"email", "emailVerified", "phone", "phoneVerified",
						"birthday", "bio", "avatarId", "createdAt", "updatedAt"));
	}
}
