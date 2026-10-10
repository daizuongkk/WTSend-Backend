package com.wtsend.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailResponse;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.libs.EmailTemplateRender;
import com.wtsend.backend.libs.TokenGenerator;
import com.wtsend.backend.model.EmailVerificationToken;
import com.wtsend.backend.model.PasswordResetToken;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.EmailVerificationTokenRepository;
import com.wtsend.backend.repository.OtpRepository;
import com.wtsend.backend.repository.PasswordResetTokenRepository;
import com.wtsend.backend.repository.UserRepository;

/**
 * The order of "store the token" and "send the mail", which is the whole bug.
 *
 * <p>
 * Stored after the send, a Redis failure between the two left the user holding
 * a mail whose link pointed at a token that did not exist -- and the retry sent
 * another, so five attempts produced five dead links and nothing recorded.
 * Writing the cooldown up front would only have reversed it: the retry would
 * read its own cooldown, the consumer would call that a success, and the mail
 * would never go at all.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailTokenOrderingTest {

	@Mock
	private OtpRepository otpRepository;
	@Mock
	private EmailTemplateRender templateRender;
	@Mock
	private EmailVerificationTokenRepository verificationTokenRepo;
	@Mock
	private PasswordResetTokenRepository resetTokenRepo;
	@Mock
	private TokenGenerator tokenGenerator;
	@Mock
	private UserRepository userRepo;
	@Mock
	private JwtService jwtService;
	@Mock
	private RefreshTokenService refreshTokenService;

	@InjectMocks
	private EmailService emailService;

	private Emails emails;
	private User user;

	/**
	 * The cooldown as it stood at each save, recorded at the time.
	 *
	 * <p>
	 * Not an ArgumentCaptor: the repository hands back the very instance it was
	 * given, exactly as a Redis repository does, so the code's later
	 * {@code setCooldownUntil} mutates the object a captor is holding and both
	 * captured values read as the final one.
	 */
	private final List<Instant> verificationCooldownsAtSave = new ArrayList<>();
	private final List<Instant> resetCooldownsAtSave = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		user = User.builder().email("someone@example.com").displayName("Ai Đó").build();
		user.setId("user-1");

		// The Resend client is built in @PostConstruct from the api key, so there
		// is no constructor seam for it. Replaced directly rather than booting a
		// context, which would want a real key.
		Resend resend = mock(Resend.class);
		emails = mock(Emails.class);
		when(resend.emails()).thenReturn(emails);
		ReflectionTestUtils.setField(emailService, "resend", resend);
		ReflectionTestUtils.setField(emailService, "fromEmail", "no-reply@example.com");
		ReflectionTestUtils.setField(emailService, "frontendUrl", "https://app.example.com");

		when(templateRender.render(any(), any())).thenReturn("<html></html>");
		when(tokenGenerator.generateVerificationToken()).thenReturn("the-token");
		when(verificationTokenRepo.findByUserId("user-1")).thenReturn(Optional.empty());
		when(resetTokenRepo.findByUserId("user-1")).thenReturn(Optional.empty());
		when(verificationTokenRepo.save(any())).thenAnswer(inv -> {
			EmailVerificationToken token = inv.getArgument(0);
			verificationCooldownsAtSave.add(token.getCooldownUntil());
			return token;
		});
		when(resetTokenRepo.save(any())).thenAnswer(inv -> {
			PasswordResetToken token = inv.getArgument(0);
			resetCooldownsAtSave.add(token.getCooldownUntil());
			return token;
		});
		// Built before the outer stub, not inside it: stubbing the inner mock
		// while when(emails.send(...)) is still open is UnfinishedStubbing.
		CreateEmailResponse sent = mock(CreateEmailResponse.class);
		when(sent.getId()).thenReturn("resend-1");
		when(emails.send(any())).thenReturn(sent);
	}

	@Test
	void theVerificationTokenExistsBeforeTheMailIsSent() throws Exception {
		emailService.sendVerifyLink(user);

		InOrder order = inOrder(verificationTokenRepo, emails);
		order.verify(verificationTokenRepo).deleteAllByUserId("user-1");
		order.verify(verificationTokenRepo).save(any());
		order.verify(emails).send(any());
	}

	@Test
	void theVerificationCooldownIsWrittenOnlyAfterASuccessfulSend() throws Exception {
		emailService.sendVerifyLink(user);

		// First save: the usable token, no cooldown. Second: the cooldown, once
		// the mail is actually on its way.
		assertThat(verificationCooldownsAtSave).hasSize(2);
		assertThat(verificationCooldownsAtSave.get(0)).isNull();
		assertThat(verificationCooldownsAtSave.get(1)).isNotNull();
	}

	@Test
	void aFailedSendLeavesNoCooldownToBlockTheRetry() throws Exception {
		when(emails.send(any())).thenThrow(new ResendException("Resend is down"));

		assertThatThrownBy(() -> emailService.sendVerifyLink(user))
				.isInstanceOf(AppException.class)
				.extracting(e -> ((AppException) e).getErrorCode())
				.isEqualTo(ErrorCode.EMAIL_SEND_FAILED);

		// One save, and it carried no cooldown. With the cooldown written up
		// front, the consumer's retry would read it, report success, and the
		// user would never get the mail.
		assertThat(verificationCooldownsAtSave).containsExactly((Instant) null);
	}

	@Test
	void theResetTokenFollowsTheSameOrder() throws Exception {
		emailService.sendPasswordResetLink(user);

		InOrder order = inOrder(resetTokenRepo, emails);
		order.verify(resetTokenRepo).deleteAllByUserId("user-1");
		order.verify(resetTokenRepo).save(any());
		order.verify(emails).send(any());

		assertThat(resetCooldownsAtSave).hasSize(2);
		assertThat(resetCooldownsAtSave.get(0)).isNull();
		assertThat(resetCooldownsAtSave.get(1)).isNotNull();
	}

	@Test
	void aLiveCooldownStillRefusesBeforeAnythingIsWritten() {
		PasswordResetToken live = PasswordResetToken.builder()
				.token("earlier")
				.userId("user-1")
				.cooldownUntil(Instant.now().plusSeconds(30))
				.build();
		when(resetTokenRepo.findByUserId("user-1")).thenReturn(Optional.of(live));

		assertThatThrownBy(() -> emailService.sendPasswordResetLink(user))
				.isInstanceOf(AppException.class)
				.extracting(e -> ((AppException) e).getErrorCode())
				.isEqualTo(ErrorCode.PASSWORD_RESET_COOLDOWN);

		// Nothing retired: the link already in the user's inbox stays usable.
		verify(resetTokenRepo, org.mockito.Mockito.never()).deleteAllByUserId(any());
		verify(resetTokenRepo, org.mockito.Mockito.never()).save(any());
	}

	/**
	 * The token in the mail has to be the one that was stored, which is the
	 * other half of the ordering above: getting the order right is no use if the
	 * link carries a different value.
	 */
	@Test
	void theLinkInTheMailCarriesTheTokenThatWasStored() {
		emailService.sendVerifyLink(user);

		ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);
		verify(templateRender).render(any(), model.capture());
		assertThat(model.getValue().get("verifyLink")).asString().contains("the-token");
	}
}
