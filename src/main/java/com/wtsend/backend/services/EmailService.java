package com.wtsend.backend.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.wtsend.backend.dto.response.AuthResponse;
import com.wtsend.backend.exceptions.EmailException;
import com.wtsend.backend.exceptions.RequestException;
import com.wtsend.backend.exceptions.TooManyRequestException;
import com.wtsend.backend.libs.EmailTemplateRender;
import com.wtsend.backend.libs.TokenGenerator;
import com.wtsend.backend.model.EmailVerificationToken;
import com.wtsend.backend.model.Otp;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.EmailVerificationTokenRepository;
import com.wtsend.backend.repository.OtpRepository;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.interfaces.IEmailService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService implements IEmailService {

	@Value("${resend.api-key}")
	private String apiKey;

	@Value("${resend.from}")
	private String fromEmail;

	@Value("${app.frontend.url}")
	private String frontendUrl;
	private Resend resend;

	private final OtpRepository otpRepository;
	private static final int MAX_OTP_ATTEMPTS = 5;
	private final PasswordEncoder passwordEncoder;

	private final EmailTemplateRender templateRender;
	private final EmailVerificationTokenRepository emailVerificationTokenRepo;
	private final TokenGenerator tokenGenerator;
	private final UserRepository userRepo;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;

	@PostConstruct
	public void init() {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("Missing resend.api-key");
		}

		this.resend = new Resend(apiKey);
	}

	@Override
	public AuthResponse verifyEmail(String token) {

		EmailVerificationToken tokenEntity = emailVerificationTokenRepo
				.findByToken(token)
				.orElseThrow(() -> new RequestException(
						"Invalid or expired token"));
		User user = userRepo.findById(
				tokenEntity.getUserId()).orElseThrow();
		user.setEmailVerified(true);
		userRepo.save(user);

		emailVerificationTokenRepo.deleteAllByUserId(user.getId());
		emailVerificationTokenRepo.delete(tokenEntity);
		return AuthResponse.builder()
				.email(user.getEmail())
				.emailVerified(true)
				.accessToken(jwtService.generateToken(user))
				.refreshToken(refreshTokenService.create(user).getToken()).build();
	}

	@Override
	public void sendVerifyLink(User user) {
		try {

			EmailVerificationToken exitsToken = emailVerificationTokenRepo.findByUserId(user.getId()).orElse(null);

			if (exitsToken != null && exitsToken.getCooldownUntil() != null &&
					exitsToken.getCooldownUntil().isAfter(Instant.now())) {

				long retryAfter = Duration.between(
						Instant.now(),
						exitsToken.getCooldownUntil()).getSeconds();

				throw new TooManyRequestException(
						"Please wait before requesting another verification email " +
								retryAfter);

			}

			String token = tokenGenerator.generateVerificationToken();

			CreateEmailOptions params = CreateEmailOptions.builder().from(fromEmail).to(user.getEmail())
					.subject("Xác thực email")
					.html(templateRender.render("emails/verify-email", Map.of(
							"verifyLink", frontendUrl + "/email-verify?token=" + token)))
					.build();

			CreateEmailResponse response = resend.emails().send(params);

			emailVerificationTokenRepo.deleteAllByUserId(user.getId());

			emailVerificationTokenRepo
					.save(EmailVerificationToken.builder().token(token).userId(user.getId()).expiresIn(15L)
							.cooldownUntil(Instant.now().plusSeconds(30)).build());

			log.info(
					"Link email sent successfully. EmailId={}, To={}",
					response.getId(),
					user.getEmail());

		} catch (Exception e) {
			log.error(
					"Failed to send verify link email to {}",
					user.getEmail(),
					e);
		}

	}

	public void sendOtp(String toEmail, String otp) {
		try {

			CreateEmailOptions params = CreateEmailOptions.builder()
					.from(fromEmail)
					.to(toEmail)
					.subject("Mã xác thực OTP")
					.html(templateRender.render("emails/verify-email", null))
					.build();

			CreateEmailResponse response = resend.emails().send(params);

			log.info(
					"OTP email sent successfully. EmailId={}, To={}",
					response.getId(),
					toEmail);

		} catch (Exception e) {

			log.error(
					"Failed to send OTP email to {}",
					toEmail,
					e);

			throw new EmailException(
					"Failed to send OTP email",
					e);
		}
	}

	public void verifyOtp(String userId, String inputOtp) {
		Otp otp = otpRepository.findById(userId)
				.orElseThrow(() -> new RuntimeException("OTP has expired or does not exist"));

		if (otp.getAttempts() >= MAX_OTP_ATTEMPTS) {
			otpRepository.deleteById(userId);
			throw new EmailException("You have entered incorrectly too many times. Please resend new OTP code");
		}

		boolean matched = passwordEncoder.matches(inputOtp, otp.getCode());

		if (!matched) {
			int newAttempts = otp.getAttempts() + 1;

			if (newAttempts >= MAX_OTP_ATTEMPTS) {
				otpRepository.deleteById(userId);
				throw new EmailException("You have entered incorrectly too many times. Please resend new OTP code");
			}

			otp.setAttempts(newAttempts);
			otpRepository.save(otp);

			throw new EmailException("OTP code is incorrect. You still "
					+ (MAX_OTP_ATTEMPTS - newAttempts)
					+ " try times");
		}

		otpRepository.deleteById(userId);

	}
}