package com.wtsend.backend.services.interfaces;

import org.springframework.stereotype.Service;

import com.wtsend.backend.dto.response.AuthResponse;
import com.wtsend.backend.model.User;

@Service
public interface IEmailService {
	AuthResponse verifyEmail(String token);

	void sendVerifyLink(User user);

}
