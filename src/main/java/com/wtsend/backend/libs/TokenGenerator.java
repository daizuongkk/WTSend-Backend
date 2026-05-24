package com.wtsend.backend.libs;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class TokenGenerator {
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	public String generateVerificationToken() {
		byte[] randomBytes = new byte[32];

		SECURE_RANDOM.nextBytes(randomBytes);

		return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(randomBytes);
	}
}
