package com.wtsend.backend.services.interfaces;

import com.wtsend.backend.dto.response.AuthResponse;
import com.wtsend.backend.model.User;

public interface IEmailService {
	AuthResponse verifyEmail(String token);

	void sendVerifyLink(User user);

	/** Resolves the user itself, so callers don't need repository access. */
	void sendVerifyLink(String userId);

}
