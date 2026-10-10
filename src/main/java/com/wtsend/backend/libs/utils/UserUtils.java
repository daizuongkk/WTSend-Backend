package com.wtsend.backend.libs.utils;


import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.wtsend.backend.dto.FacebookUser;
import com.wtsend.backend.dto.request.SignUpRequest;
import com.wtsend.backend.dto.response.FriendResponse;
import com.wtsend.backend.dto.response.Sender;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.model.User;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserUtils {
	private final PasswordEncoder passwordEncoder;
	private final ModelMapper modelMapper;

	/**
	 * Usernames for accounts created through an identity provider.
	 *
	 * <p>
	 * Derived from the provider's own id rather than the local part of the email.
	 * That part is not unique -- {@code john@gmail.com} and
	 * {@code john@company.com} both yield "john" -- and the column is UNIQUE, so
	 * the second such sign-in failed on a constraint violation.
	 *
	 * <p>
	 * Capped at 20 characters because that is the limit
	 * {@link com.wtsend.backend.dto.request.UpdateUserRequest} enforces; a longer
	 * one would be stored happily and then block the user from ever saving their
	 * profile. A Google subject id is 21 digits, so truncation is the norm rather
	 * than the exception, which is why the result is still checked for collisions
	 * by the caller.
	 */
	public static String providerUsername(String prefix, String providerId) {
		String candidate = prefix + providerId;
		return candidate.length() <= MAX_USERNAME_LENGTH
				? candidate
				: candidate.substring(0, MAX_USERNAME_LENGTH);
	}

	/** Matches the @Size(max) on UpdateUserRequest.username. */
	public static final int MAX_USERNAME_LENGTH = 20;

	public User toUser(SignUpRequest request) {

		return User.builder().password(passwordEncoder.encode(request.getPassword()))
				.email(request.getEmail()).displayName(request.getEmail().split("@")[0])
				.build();
	}

	public UserResponse toUserResponse(User user) {
		UserResponse userResponse = modelMapper.map(user, UserResponse.class);
		if (user.getBirthday() != null) {
			userResponse.setBirthday(user.getBirthday().toString());

		}
		return userResponse;
	}

	public FriendResponse toFriendResponse(User user) {
		return modelMapper.map(user, FriendResponse.class);
	}

	public Sender toSender(User user) {
		return Sender.builder()
				.id(user.getId())
				.displayName(user.getDisplayName())
				.avatarUrl(user.getAvatarUrl())
				.build();
	}

	public User facebookUserToEntity(FacebookUser facebookUser) {
		return User.builder()
				.username(providerUsername("fb_", facebookUser.id()))
				.displayName(displayNameOr(facebookUser.name(), facebookUser.email()))
				.email(facebookUser.email())
				.avatarUrl(facebookUser.pictureUrl())
				// NOT verified, although Graph did return an address. Facebook
				// exposes no verified flag for it, so it is not proof that the user
				// owns the mailbox -- and that proof is the only thing this flag
				// means. The ordinary verification mail asks for it, as for any
				// other sign-up.
				.emailVerified(false)
				.build();
	}

	public User googleOAuth2PayloadToEntity(GoogleIdToken.Payload payload) {
		return User.builder()
				.username(providerUsername("google_", payload.getSubject()))
				.displayName(displayNameOr(claim(payload, "name"), payload.getEmail()))
				.email(payload.getEmail())
				.avatarUrl(claim(payload, "picture"))
				.emailVerified(Boolean.TRUE.equals(payload.getEmailVerified()))
				.build();
	}

	/**
	 * Both claims read through here are optional: {@code picture} is absent for
	 * an account with no profile photo, {@code name} for one that granted only
	 * the email scope, and {@code email_verified} comes back as a boxed null.
	 * {@code .toString()} on any of them was an NPE inside a sign-in that was
	 * otherwise perfectly valid.
	 */
	private static String claim(GoogleIdToken.Payload payload, String name) {
		Object value = payload.get(name);
		return value == null ? null : value.toString();
	}

	/** {@code display_name} is NOT NULL and neither provider promises to send one. */
	private static String displayNameOr(String name, String email) {
		return name == null || name.isBlank() ? email.split("@")[0] : name;
	}
}
