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
		return fitToUsernameRules(prefix + providerId);
	}

	/**
	 * A username derived from the address, for accounts nobody was asked to name
	 * one for.
	 *
	 * <p>
	 * {@code signUp} never set a username at all, so every password account had
	 * NULL -- and the column is the only key {@code GET /users/search} accepts,
	 * which means those accounts could not be found by anyone wanting to add
	 * them as a friend. An empty profile field was the visible half of that.
	 *
	 * <p>
	 * Only {@code [a-z0-9._-]} survives, because this value ends up in a query
	 * string: a {@code +} in an email local part is legal and would arrive at
	 * the server as a space. The result is not unique on its own -- two people
	 * at different domains share a local part -- so the caller still has to put
	 * it through {@code withFreeUsername}.
	 */
	public static String usernameFromEmail(String email) {
		String localPart = email.substring(0, email.indexOf('@'))
				.toLowerCase()
				.replaceAll("[^a-z0-9._-]", "");

		return fitToUsernameRules(localPart.isEmpty() ? "user" : localPart);
	}

	/**
	 * Brings a candidate inside the bounds
	 * {@link com.wtsend.backend.dto.request.UpdateUserRequest} enforces.
	 *
	 * <p>
	 * Both ends matter, and the lower one is easy to forget: a username outside
	 * 8-20 characters is stored happily and then rejected by the only endpoint
	 * that can change it, so the user is locked out of saving their own profile
	 * for ever. {@code a@example.com} yields a one-character local part, and a
	 * Google subject id yields 28 characters with the prefix.
	 */
	private static String fitToUsernameRules(String candidate) {
		if (candidate.length() > MAX_USERNAME_LENGTH)
			return candidate.substring(0, MAX_USERNAME_LENGTH);

		return candidate.length() >= MIN_USERNAME_LENGTH
				? candidate
				: candidate + "0".repeat(MIN_USERNAME_LENGTH - candidate.length());
	}

	/** Both match the @Size on UpdateUserRequest.username -- change them together. */
	public static final int MAX_USERNAME_LENGTH = 20;
	public static final int MIN_USERNAME_LENGTH = 8;

	public User toUser(SignUpRequest request) {

		return User.builder().password(passwordEncoder.encode(request.getPassword()))
				.email(request.getEmail()).displayName(request.getEmail().split("@")[0])
				// Derived, not left null. The caller settles collisions.
				.username(usernameFromEmail(request.getEmail()))
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
