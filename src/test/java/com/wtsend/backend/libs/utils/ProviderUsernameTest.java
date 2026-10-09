package com.wtsend.backend.libs.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * OAuth usernames were the local part of the email, which is not unique:
 * john@gmail.com and john@company.com both produced "john", and the column is
 * UNIQUE -- so the second such sign-in died on a constraint violation.
 */
class ProviderUsernameTest {

	@Test
	void aGoogleSubjectIsTruncatedToTheUsernameLimit() {
		// Google subject ids are 21 digits, so "google_" + id is 28 characters --
		// past the @Size(max = 20) on UpdateUserRequest. Storing a longer one
		// would block the user from ever saving their profile.
		String username = UserUtils.providerUsername("google_", "123456789012345678901");

		assertThat(username).hasSize(UserUtils.MAX_USERNAME_LENGTH);
		assertThat(username).startsWith("google_");
	}

	@Test
	void aShortIdIsLeftAlone() {
		assertThat(UserUtils.providerUsername("fb_", "1234567")).isEqualTo("fb_1234567");
	}

	@Test
	void differentProvidersCannotCollideOnTheSameId() {
		assertThat(UserUtils.providerUsername("fb_", "1234567"))
				.isNotEqualTo(UserUtils.providerUsername("google_", "1234567"));
	}

	@Test
	void truncationCanStillCollide() {
		// Which is exactly why AuthService checks existsByUsername before the
		// insert rather than trusting this to be unique on its own.
		assertThat(UserUtils.providerUsername("google_", "111111111111112222"))
				.isEqualTo(UserUtils.providerUsername("google_", "111111111111113333"));
	}
}
