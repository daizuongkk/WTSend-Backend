package com.wtsend.backend.libs.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * How a username is produced for an account nobody was asked to name.
 *
 * <p>
 * Two separate bugs live here. OAuth usernames were once the local part of the
 * email, which is not unique -- john@gmail.com and john@company.com both
 * produced "john" against a UNIQUE column -- and password signups set no
 * username at all, which left them unfindable by the one endpoint that looks
 * users up. Both ends of the length rule matter too: outside 8-20 characters
 * the value stores fine and is then rejected by the only endpoint that can
 * change it.
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
	void anIdTooShortToReachTheMinimumIsPadded() {
		// Stored happily at 6 characters and then refused by PUT /api/users,
		// which is the only way to change it: locked out of their own profile.
		String username = UserUtils.providerUsername("fb_", "123");

		assertThat(username).hasSizeGreaterThanOrEqualTo(UserUtils.MIN_USERNAME_LENGTH);
		assertThat(username).startsWith("fb_123");
	}

	@Test
	void anEmailBecomesItsLocalPart() {
		assertThat(UserUtils.usernameFromEmail("dainguyenjr0108@gmail.com"))
				.isEqualTo("dainguyenjr0108");
	}

	@Test
	void charactersThatWouldNotSurviveAQueryStringAreDropped() {
		// This value is read back through GET /users/search?username=..., where a
		// legal + in a local part arrives as a space.
		assertThat(UserUtils.usernameFromEmail("Dai+Nguyen!*@gmail.com"))
				.isEqualTo("dainguyen");
	}

	@Test
	void aLocalPartTooShortIsPaddedAndOneTooLongIsCut() {
		assertThat(UserUtils.usernameFromEmail("a@example.com"))
				.hasSize(UserUtils.MIN_USERNAME_LENGTH).startsWith("a");
		assertThat(UserUtils.usernameFromEmail("averyverylongmailboxname@example.com"))
				.hasSize(UserUtils.MAX_USERNAME_LENGTH);
	}

	@Test
	void aLocalPartWithNothingUsableFallsBackRatherThanBeingEmpty() {
		// "+++" survives the filter as nothing at all, and an empty username is
		// the bug this whole class exists to stop.
		assertThat(UserUtils.usernameFromEmail("+++@example.com"))
				.hasSizeGreaterThanOrEqualTo(UserUtils.MIN_USERNAME_LENGTH)
				.startsWith("user");
	}

	@Test
	void twoDomainsSharingALocalPartStillCollide() {
		// Which is why AuthService.signUp puts this through withFreeUsername
		// rather than trusting it to be unique.
		assertThat(UserUtils.usernameFromEmail("john@gmail.com"))
				.isEqualTo(UserUtils.usernameFromEmail("john@company.com"));
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
