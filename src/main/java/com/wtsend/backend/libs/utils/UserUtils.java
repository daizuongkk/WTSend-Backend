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
				.email(request.getEmail()).displayName(request.getEmail().split("@")[0]).build();
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
				.displayName(facebookUser.name())
				.email(facebookUser.email())
				.avatarUrl(facebookUser.pictureUrl())
				// Facebook only returns an email it has verified, and
				// FacebookAuthService refuses a profile without one.
				.emailVerified(true)
				.build();
	}

	public User googleOAuth2PayloadToEntity(GoogleIdToken.Payload payload) {
		return User.builder()
				.username(providerUsername("google_", payload.getSubject()))
				.displayName(payload.get("name").toString())
				.email(payload.getEmail())
				.avatarUrl(payload.get("picture").toString())
				.emailVerified(payload.getEmailVerified())
				.build();
	}
}
