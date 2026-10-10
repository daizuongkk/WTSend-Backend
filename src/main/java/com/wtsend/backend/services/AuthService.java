package com.wtsend.backend.services;

import java.time.Instant;
import java.util.function.Supplier;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.wtsend.backend.dto.request.ChangePasswordRequest;
import com.wtsend.backend.dto.FacebookUser;
import com.wtsend.backend.dto.request.FacebookAuthRequest;
import com.wtsend.backend.dto.request.ForgotPasswordRequest;
import com.wtsend.backend.dto.request.ResetPasswordRequest;
import com.wtsend.backend.dto.request.SignInRequest;
import com.wtsend.backend.dto.request.SignUpRequest;
import com.wtsend.backend.dto.response.AuthResponse;
import com.wtsend.backend.dto.response.UserResponse;
import com.wtsend.backend.event.PasswordResetRequestedEvent;
import com.wtsend.backend.event.VerificationEmailRequestedEvent;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.model.User;
import com.wtsend.backend.model.UserIdentity;
import com.wtsend.backend.model.enums.AuthProvider;
import com.wtsend.backend.repository.UserIdentityRepository;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.interfaces.IAuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService implements IAuthService {

	private final UserRepository userRepository;
	private final UserIdentityRepository identityRepository;
	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final UserUtils userMapper;
	private final PasswordEncoder passwordEncoder;
	private final GoogleAuthService googleAuthService;
	private final ApplicationEventPublisher eventPublisher;
	private final EmailService emailService;
	private final FacebookAuthService facebookAuthService;

	public AuthResponse signIn(SignInRequest req) {
		UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
				req.getEmail().trim(), req.getPassword());

		Authentication auth = authenticationManager.authenticate(usernamePasswordAuthenticationToken);

		return session((User) auth.getPrincipal());
	}

	/**
	 * The response every successful sign-in returns, whichever door it came
	 * through.
	 *
	 * <p>
	 * An unverified account gets an access token and no refresh token: it can
	 * read {@code /api/users/me} and verify, and nothing else, so a session it
	 * cannot renew is exactly right. The verification mail is published rather
	 * than sent, so a cooldown rejection cannot fail the login that triggered it.
	 */
	private AuthResponse session(User user) {
		if (!user.isEmailVerified()) {
			eventPublisher.publishEvent(new VerificationEmailRequestedEvent(user.getId()));
			return AuthResponse.builder()
					.email(user.getEmail())
					.emailVerified(false)
					.accessToken(jwtService.generateToken(user))
					.build();
		}

		return AuthResponse.builder()
				.email(user.getEmail())
				.emailVerified(true)
				.accessToken(jwtService.generateToken(user))
				.refreshToken(refreshTokenService.create(user).token())
				.build();
	}

	@Transactional
	public AuthResponse googleLogin(String token) {
		GoogleIdToken.Payload payload = googleAuthService.verify(token);

		User user = resolveProviderAccount(AuthProvider.GOOGLE, payload.getSubject(), payload.getEmail(),
				Boolean.TRUE.equals(payload.getEmailVerified()),
				() -> userMapper.googleOAuth2PayloadToEntity(payload));

		return session(user);
	}

	/**
	 * Signs in through Facebook, creating the account on first use.
	 *
	 * <p>
	 * {@code providerVerifiedEmail} is false and that is not an oversight.
	 * Facebook returns an address but exposes no flag saying it verified one, so
	 * it cannot stand as proof of ownership -- which means a Facebook login never
	 * links itself onto an existing account and never sets
	 * {@code emailVerified}. A first-time Facebook user verifies by mail like
	 * anyone else.
	 */
	@Override
	@Transactional
	public AuthResponse facebookLogin(FacebookAuthRequest request) {
		FacebookUser facebookUser = facebookAuthService.verify(request.getAccessToken());

		User user = resolveProviderAccount(AuthProvider.FACEBOOK, facebookUser.id(), facebookUser.email(),
				false, () -> userMapper.facebookUserToEntity(facebookUser));

		return session(user);
	}

	/**
	 * Finds the account behind a provider login, creating or linking as needed.
	 *
	 * <p>
	 * The key is the provider's own subject id, held in {@code user_identity},
	 * not the email address. Matching on email was an account-takeover path:
	 * anybody who could get a provider to report a victim's address signed
	 * straight into the victim's account, and only one of the two providers here
	 * promises the address was ever verified.
	 *
	 * <p>
	 * So a subject this app has not seen before is linked onto an existing
	 * account only when the provider says it verified that address. Otherwise the
	 * caller is refused and told to sign in the way they already can -- a
	 * deliberate dead end rather than a silent takeover.
	 *
	 * <p>
	 * An address with no account at all is always allowed through. The new
	 * account simply starts unverified unless the provider vouched for it, which
	 * is also what stops a provider-reported address from squatting a mailbox
	 * nobody has proved they own: unverified registrations are swept after 24
	 * hours.
	 */
	private User resolveProviderAccount(AuthProvider provider, String subject, String email,
			boolean providerVerifiedEmail, Supplier<User> newAccount) {

		var known = identityRepository.findBySubject(provider, subject);
		if (known.isPresent())
			return known.get().getUser();

		User user = userRepository.findByEmail(email).orElse(null);

		if (user == null) {
			user = userRepository.save(withFreeUsername(newAccount.get()));
		} else if (!providerVerifiedEmail) {
			throw new AppException(ErrorCode.PROVIDER_LINK_NOT_ALLOWED)
					.withDetail(provider + " did not verify the address of existing userId=" + user.getId());
		} else if (!user.isEmailVerified()) {
			// The provider just proved ownership of the mailbox, which is the one
			// thing the verification mail exists to establish.
			user.setEmailVerified(true);
			userRepository.save(user);
		}

		identityRepository.save(linked(user, provider, subject, email));
		return user;
	}

	/**
	 * Records the identity, or fills in the subject on the placeholder row that
	 * {@code V6__user_identity.sql} backfilled for an account that predates the
	 * table. {@code uk_user_identity_user_provider} is what makes one row per
	 * provider the only possible outcome.
	 */
	private UserIdentity linked(User user, AuthProvider provider, String subject, String email) {
		UserIdentity identity = identityRepository.findByUser_IdAndProvider(user.getId(), provider)
				.orElseGet(() -> UserIdentity.builder().user(user).provider(provider).build());

		identity.setProviderUserId(subject);
		identity.setEmail(email);
		identity.setLinkedAt(Instant.now());
		return identity;
	}

	/**
	 * Provider ids are truncated to fit the 20-character username limit, so two
	 * of them can collide even though the ids themselves do not. Settles it
	 * before the insert instead of letting the UNIQUE constraint reject a
	 * sign-in that is otherwise perfectly valid.
	 */
	private User withFreeUsername(User user) {
		String base = user.getUsername();
		String candidate = base;

		for (int suffix = 1; userRepository.existsByUsername(candidate); suffix++) {
			String tail = String.valueOf(suffix);
			int keep = Math.min(base.length(), UserUtils.MAX_USERNAME_LENGTH - tail.length());
			candidate = base.substring(0, keep) + tail;
		}

		user.setUsername(candidate);
		return user;
	}

	/**
	 * Revokes only the presented refresh token. Keying off the user id would pick
	 * an arbitrary one of their sessions and log out the wrong device, and reading
	 * the access token would make logout impossible once that token expires.
	 */
	public String signOut(String refreshToken) {
		refreshTokenService.revoke(refreshToken);
		return "Signout successfully";
	}

	@Transactional
	public UserResponse signUp(SignUpRequest request) {

		if (userRepository.existsByEmail(request.getEmail())) {
			throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}

		User newUser = userMapper.toUser(request);

		User savedUser = userRepository.save(newUser);

		// Published, not sent: the listener runs after this transaction commits, so
		// the connection is released before the blocking call to Resend. A send
		// failure no longer rolls the new account back -- an unverified account is
		// deleted after 24 hours instead.
		eventPublisher.publishEvent(new VerificationEmailRequestedEvent(savedUser.getId()));

		return userMapper.toUserResponse(savedUser);

	}

	public AuthResponse refreshToken(String token) {

		// One operation, not verify-then-revoke: two concurrent calls with the
		// same cookie both used to read the record before either delete landed,
		// and one token became two live chains. A replay now revokes the whole
		// family and then fails like any invalid token.
		var rotated = refreshTokenService.rotate(token);
		User user = rotated.user();
		var newRfToken = rotated.refreshToken();

		String newAccessToken = jwtService.generateToken(user);
		return AuthResponse.builder()
				.refreshToken(newRfToken.token())
				.accessToken(newAccessToken)
				.build();
	}

	/**
	 * Requests a reset link.
	 *
	 * <p>
	 * Every outcome returns the same 200 and the same body. All three branches
	 * below are observable only in the mailbox, which is the point: reporting
	 * EMAIL_NOT_VERIFIED for the middle one answered "does this address have an
	 * account?" to anybody who cared to ask, and distinguished a registered
	 * address from an unregistered one on the strength of a single status code.
	 *
	 * <p>
	 * An account whose email is not verified gets the verification link rather
	 * than a reset link: letting someone set a password through an address they
	 * have not proved they own is the hole the whole verification flow exists to
	 * close. Sending that mail rather than nothing still leaves them a way
	 * forward -- and verifying also rescues the account from
	 * UnverifiedAccountCleanupJob.
	 *
	 * <p>
	 * An account with no password (signed up through a provider) is allowed:
	 * owning the mailbox is the same proof the link relies on, and the result is
	 * that password login becomes available alongside the provider.
	 */
	@Override
	public void forgotPassword(ForgotPasswordRequest request) {
		User user = userRepository.findByEmail(request.getEmail()).orElse(null);
		if (user == null) {
			log.info("Password reset requested for an address with no account");
			return;
		}

		if (!user.isEmailVerified()) {
			eventPublisher.publishEvent(new VerificationEmailRequestedEvent(user.getId()));
			log.info("Password reset requested before verification; sent the verification link instead, userId={}",
					user.getId());
			return;
		}

		eventPublisher.publishEvent(new PasswordResetRequestedEvent(user.getId()));
	}

	/**
	 * Consumes the token, sets the password, and drops every session.
	 *
	 * <p>
	 * Revoking follows changePassword: a reset is what someone does when they
	 * believe the account is compromised, so a session holding an old refresh
	 * token must not outlive it. Nothing is returned -- the client signs in with
	 * the new password, which also confirms it is what they meant to set.
	 */
	@Override
	@Transactional
	public void resetPassword(ResetPasswordRequest request) {
		User user = emailService.consumePasswordResetToken(request.getToken());

		user.setPassword(passwordEncoder.encode(request.getNewPassword()));
		userRepository.save(user);

		// Kills every credential issued before now, refresh AND access: access
		// tokens are stateless, so JwtConfig checks this same marker. Enumerating
		// refresh tokens could never reach the access tokens already out there,
		// which stayed valid for up to their full five minutes.
		refreshTokenService.revokeEverythingIssuedBefore(user.getId());
	}

	@Override
	@Transactional
	public AuthResponse changePassword(ChangePasswordRequest request, String userId) {
		// This app is an OAuth2 resource server, so the principal is a Jwt, not a
		// User -- casting the SecurityContext principal here threw ClassCastException.
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + userId));

		if (user.getPassword() == null)
			throw new AppException(ErrorCode.NO_PASSWORD_SET).withDetail("userId=" + userId);

		if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
			throw new AppException(ErrorCode.WRONG_CURRENT_PASSWORD).withDetail("userId=" + userId);

		}

		user.setPassword(passwordEncoder.encode(request.getNewPassword()));
		userRepository.save(user);

		// Any session still holding an old refresh token must not survive.
		refreshTokenService.revokeEverythingIssuedBefore(userId);

		// Except this one. The caller just proved they know the current password, so
		// logging them out of the device they are using would be a worse outcome than
		// the one this protects against -- and silently expiring their session minutes
		// later, which is what returning nothing did, is worse still.
		return AuthResponse.builder()
				.email(user.getEmail())
				.emailVerified(user.isEmailVerified())
				.accessToken(jwtService.generateToken(user))
				.refreshToken(refreshTokenService.create(user).token())
				.build();
	}

}
