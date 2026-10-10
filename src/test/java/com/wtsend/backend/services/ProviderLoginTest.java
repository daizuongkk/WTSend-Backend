package com.wtsend.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.dto.FacebookUser;
import com.wtsend.backend.dto.request.FacebookAuthRequest;
import com.wtsend.backend.event.VerificationEmailRequestedEvent;
import com.wtsend.backend.libs.utils.UserUtils;
import com.wtsend.backend.model.User;
import com.wtsend.backend.model.UserIdentity;
import com.wtsend.backend.model.enums.AuthProvider;
import com.wtsend.backend.repository.UserIdentityRepository;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.RefreshTokenService.IssuedRefreshToken;

/**
 * Signing in through Google and Facebook.
 *
 * <p>
 * What these pin is that the account is found by the provider's subject id and
 * not by the email address. The old lookup was {@code findByEmail}, so anyone
 * who could get a provider to report a victim's address signed into the
 * victim's account -- and Facebook publishes no verified flag for the address
 * it returns, so that was not a theoretical provider.
 */
@ExtendWith(MockitoExtension.class)
class ProviderLoginTest {

	private static final String GOOGLE_SUBJECT = "109876543210987654321";
	private static final String FACEBOOK_ID = "fb-7788";
	private static final String EMAIL = "someone@example.com";

	@Mock
	private UserRepository userRepository;
	@Mock
	private UserIdentityRepository identityRepository;
	@Mock
	private JwtService jwtService;
	@Mock
	private RefreshTokenService refreshTokenService;
	@Mock
	private UserUtils userMapper;
	@Mock
	private GoogleAuthService googleAuthService;
	@Mock
	private FacebookAuthService facebookAuthService;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private AuthService authService;

	private static User account(String id, boolean emailVerified) {
		User user = new User();
		user.setId(id);
		user.setEmail(EMAIL);
		user.setDisplayName("Ai Đó");
		user.setEmailVerified(emailVerified);
		return user;
	}

	private GoogleIdToken.Payload googleSaysVerified(boolean emailVerified) {
		GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
		payload.setSubject(GOOGLE_SUBJECT);
		payload.setEmail(EMAIL);
		payload.setEmailVerified(emailVerified);
		when(googleAuthService.verify("credential")).thenReturn(payload);
		return payload;
	}

	private void sessionIsIssuedFor(User user) {
		when(jwtService.generateToken(user)).thenReturn("access-token");
		when(refreshTokenService.create(user)).thenReturn(new IssuedRefreshToken("refresh-token", "family"));
	}

	private static FacebookAuthRequest facebookToken(String token) {
		FacebookAuthRequest request = new FacebookAuthRequest();
		request.setAccessToken(token);
		return request;
	}

	private UserIdentity identityOf(User user, AuthProvider provider, String subject) {
		return UserIdentity.builder().id(1L).user(user).provider(provider).providerUserId(subject).build();
	}

	@Test
	void aKnownSubjectSignsStraightIntoItsOwnAccount() {
		User user = account("user-1", true);
		googleSaysVerified(true);
		when(identityRepository.findBySubject(AuthProvider.GOOGLE, GOOGLE_SUBJECT))
				.thenReturn(Optional.of(identityOf(user, AuthProvider.GOOGLE, GOOGLE_SUBJECT)));
		sessionIsIssuedFor(user);

		var response = authService.googleLogin("credential");

		assertThat(response.getAccessToken()).isEqualTo("access-token");
		assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
		// The email address is not consulted at all on this path.
		verify(userRepository, never()).findByEmail(any());
		verify(userRepository, never()).save(any());
	}

	@Test
	void aFirstTimeProviderUserGetsAnAccountAndAnIdentityRow() {
		googleSaysVerified(true);
		when(identityRepository.findBySubject(AuthProvider.GOOGLE, GOOGLE_SUBJECT)).thenReturn(Optional.empty());
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		User fresh = account(null, true);
		fresh.setUsername(UserUtils.providerUsername("google_", GOOGLE_SUBJECT));
		when(userMapper.googleOAuth2PayloadToEntity(any())).thenReturn(fresh);
		// Truncated to 20 characters, so two different subjects can collide. The
		// Google path used to skip this and fail on the UNIQUE constraint.
		when(userRepository.existsByUsername("google_1098765432109")).thenReturn(true);
		when(userRepository.existsByUsername("google_1098765432101")).thenReturn(false);

		User saved = account("user-new", true);
		when(userRepository.save(fresh)).thenReturn(saved);
		sessionIsIssuedFor(saved);

		authService.googleLogin("credential");

		ArgumentCaptor<UserIdentity> identity = ArgumentCaptor.forClass(UserIdentity.class);
		verify(identityRepository).save(identity.capture());
		assertThat(identity.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
		assertThat(identity.getValue().getProviderUserId()).isEqualTo(GOOGLE_SUBJECT);
		assertThat(identity.getValue().getLinkedAt()).isNotNull();
	}

	@Test
	void aVerifiedProviderEmailLinksOntoTheAccountThatOwnsIt() {
		User existing = account("user-1", true);
		googleSaysVerified(true);
		when(identityRepository.findBySubject(AuthProvider.GOOGLE, GOOGLE_SUBJECT)).thenReturn(Optional.empty());
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
		when(identityRepository.findByUser_IdAndProvider("user-1", AuthProvider.GOOGLE)).thenReturn(Optional.empty());
		sessionIsIssuedFor(existing);

		authService.googleLogin("credential");

		// Google vouched for the address, so this is the same person -- no second
		// account for an address that already has one.
		verify(userRepository, never()).save(any());
		verify(identityRepository).save(any());
	}

	@Test
	void anUnverifiedProviderEmailIsRefusedRatherThanLinked() {
		User victim = account("victim", true);
		when(facebookAuthService.verify("fb-token"))
				.thenReturn(new FacebookUser(FACEBOOK_ID, "Kẻ Lạ", EMAIL, null));
		when(identityRepository.findBySubject(AuthProvider.FACEBOOK, FACEBOOK_ID)).thenReturn(Optional.empty());
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(victim));

		// The takeover this whole design exists to stop: a Facebook account
		// claiming the victim's address used to sign straight into it.
		assertThatThrownBy(() -> authService.facebookLogin(facebookToken("fb-token")))
				.isInstanceOf(AppException.class)
				.extracting(e -> ((AppException) e).getErrorCode())
				.isEqualTo(ErrorCode.PROVIDER_LINK_NOT_ALLOWED);

		verify(identityRepository, never()).save(any());
		verify(jwtService, never()).generateToken(any());
	}

	@Test
	void aBackfilledIdentityIsFilledInRatherThanDuplicated() {
		User existing = account("user-1", true);
		googleSaysVerified(true);
		when(identityRepository.findBySubject(AuthProvider.GOOGLE, GOOGLE_SUBJECT)).thenReturn(Optional.empty());
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

		// V6 backfilled accounts that predate the table with no subject, because
		// the username (google_<subject> truncated) cannot reconstruct one.
		UserIdentity placeholder = identityOf(existing, AuthProvider.GOOGLE, null);
		when(identityRepository.findByUser_IdAndProvider("user-1", AuthProvider.GOOGLE))
				.thenReturn(Optional.of(placeholder));
		sessionIsIssuedFor(existing);

		authService.googleLogin("credential");

		ArgumentCaptor<UserIdentity> saved = ArgumentCaptor.forClass(UserIdentity.class);
		verify(identityRepository).save(saved.capture());
		assertThat(saved.getValue().getId()).isEqualTo(1L);
		assertThat(saved.getValue().getProviderUserId()).isEqualTo(GOOGLE_SUBJECT);
	}

	@Test
	void linkingAProviderThatVouchedForTheAddressAlsoVerifiesTheAccount() {
		User unverified = account("user-1", false);
		googleSaysVerified(true);
		when(identityRepository.findBySubject(AuthProvider.GOOGLE, GOOGLE_SUBJECT)).thenReturn(Optional.empty());
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverified));
		when(identityRepository.findByUser_IdAndProvider("user-1", AuthProvider.GOOGLE)).thenReturn(Optional.empty());
		sessionIsIssuedFor(unverified);

		var response = authService.googleLogin("credential");

		assertThat(unverified.isEmailVerified()).isTrue();
		verify(userRepository).save(unverified);
		assertThat(response.isEmailVerified()).isTrue();
		assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
	}

	@Test
	void anAccountNoProviderVouchedForGetsNoRefreshTokenAndAVerificationMail() {
		when(facebookAuthService.verify("fb-token"))
				.thenReturn(new FacebookUser(FACEBOOK_ID, "Người Mới", EMAIL, null));
		when(identityRepository.findBySubject(AuthProvider.FACEBOOK, FACEBOOK_ID)).thenReturn(Optional.empty());
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		User fresh = account(null, false);
		fresh.setUsername("fb_" + FACEBOOK_ID);
		when(userMapper.facebookUserToEntity(any())).thenReturn(fresh);
		when(userRepository.existsByUsername(fresh.getUsername())).thenReturn(false);
		User saved = account("user-new", false);
		when(userRepository.save(fresh)).thenReturn(saved);
		when(jwtService.generateToken(saved)).thenReturn("access-token");

		var response = authService.facebookLogin(facebookToken("fb-token"));

		// Facebook does not say it verified the address, so this account verifies
		// by mail like any other sign-up -- and cannot renew a session until it
		// has.
		assertThat(response.isEmailVerified()).isFalse();
		assertThat(response.getRefreshToken()).isNull();
		verify(refreshTokenService, never()).create(any());
		verify(eventPublisher).publishEvent(any(VerificationEmailRequestedEvent.class));
	}

	/** The optional Google claims, which used to be dereferenced blind. */
	@Nested
	class GoogleClaims {

		// Neither dependency is touched by the mapping under test.
		private final UserUtils mapper = new UserUtils(null, null);

		@Test
		void aPayloadWithNoNameOrPictureStillMaps() {
			GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
			payload.setSubject(GOOGLE_SUBJECT);
			payload.setEmail(EMAIL);
			payload.setEmailVerified(true);

			User user = mapper.googleOAuth2PayloadToEntity(payload);

			// display_name is NOT NULL, so a missing name cannot stay missing.
			assertThat(user.getDisplayName()).isEqualTo("someone");
			assertThat(user.getAvatarUrl()).isNull();
			assertThat(user.isEmailVerified()).isTrue();
		}

		@Test
		void anAbsentEmailVerifiedClaimReadsAsNotVerified() {
			GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
			payload.setSubject(GOOGLE_SUBJECT);
			payload.setEmail(EMAIL);

			// Boxed and absent: unboxing it threw NPE at the builder.
			assertThat(mapper.googleOAuth2PayloadToEntity(payload).isEmailVerified()).isFalse();
		}
	}
}
