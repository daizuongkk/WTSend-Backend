package com.wtsend.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.dto.request.ForgotPasswordRequest;
import com.wtsend.backend.dto.request.ResetPasswordRequest;
import com.wtsend.backend.event.PasswordResetRequestedEvent;
import com.wtsend.backend.event.VerificationEmailRequestedEvent;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.RefreshTokenRepository;
import com.wtsend.backend.repository.UserRepository;

/**
 * Pins the decisions behind the reset flow, each of which is easy to undo by
 * accident: who gets a link, who gets told why not, and what happens to live
 * sessions afterwards.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetTest {

	private static final String EMAIL = "owner@example.com";
	private static final String USER_ID = "user-id";

	@Mock
	private UserRepository userRepository;
	@Mock
	private RefreshTokenRepository refreshTokenRepo;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private EmailService emailService;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private AuthService authService;

	private static User user(boolean emailVerified, String password) {
		User user = new User();
		user.setId(USER_ID);
		user.setEmail(EMAIL);
		user.setPassword(password);
		ReflectionTestUtils.setField(user, "emailVerified", emailVerified);
		return user;
	}

	private static ForgotPasswordRequest forgot() {
		ForgotPasswordRequest request = new ForgotPasswordRequest();
		request.setEmail(EMAIL);
		return request;
	}

	private static ResetPasswordRequest reset(String token, String newPassword) {
		ResetPasswordRequest request = new ResetPasswordRequest();
		request.setToken(token);
		request.setNewPassword(newPassword);
		return request;
	}

	@Test
	void anUnknownAddressIsAcceptedSilently() {
		// Reporting "no such account" here would turn this endpoint into a way to
		// discover which addresses are registered.
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		authService.forgotPassword(forgot());

		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void aVerifiedAccountIsSentAResetLink() {
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(true, "{bcrypt}old")));

		authService.forgotPassword(forgot());

		ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(published.capture());
		assertThat(published.getValue()).isInstanceOf(PasswordResetRequestedEvent.class);
	}

	@Test
	void anAccountWithoutAPasswordMayStillSetOne() {
		// Signed up through Google. Owning the mailbox is the same proof the link
		// relies on, so this becomes a second way to sign in rather than a refusal.
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(true, null)));

		authService.forgotPassword(forgot());

		ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(published.capture());
		assertThat(published.getValue()).isInstanceOf(PasswordResetRequestedEvent.class);
	}

	@Test
	void anUnverifiedAccountIsSentAVerificationLinkAndRefused() {
		// Resetting through an address nobody proved they own would defeat the
		// verification flow. The verification mail still goes out, so the user has
		// a way forward instead of a dead end.
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(false, "{bcrypt}old")));

		assertThatThrownBy(() -> authService.forgotPassword(forgot()))
				.isInstanceOf(AppException.class)
				.extracting(ex -> ((AppException) ex).getErrorCode())
				.isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);

		ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(published.capture());
		assertThat(published.getValue()).isInstanceOf(VerificationEmailRequestedEvent.class);
	}

	@Test
	void resettingStoresTheEncodedPasswordAndDropsEverySession() {
		User existing = user(true, "{bcrypt}old");
		when(emailService.consumePasswordResetToken("tok")).thenReturn(existing);
		when(passwordEncoder.encode("NewPass1!")).thenReturn("{bcrypt}new");

		authService.resetPassword(reset("tok", "NewPass1!"));

		assertThat(existing.getPassword()).isEqualTo("{bcrypt}new");
		verify(userRepository).save(existing);
		// A reset is what someone does when they think the account is compromised,
		// so a session holding an older refresh token must not survive it.
		verify(refreshTokenRepo).deleteByUserId(USER_ID);
	}

	@Test
	void anInvalidTokenChangesNothing() {
		when(emailService.consumePasswordResetToken("bad"))
				.thenThrow(new AppException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));

		assertThatThrownBy(() -> authService.resetPassword(reset("bad", "NewPass1!")))
				.isInstanceOf(AppException.class)
				.extracting(ex -> ((AppException) ex).getErrorCode())
				.isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);

		verify(userRepository, never()).save(any());
		verify(refreshTokenRepo, never()).deleteByUserId(any());
	}
}
