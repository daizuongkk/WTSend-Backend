package com.wtsend.backend.services.interfaces;

import org.springframework.stereotype.Service;

import com.wtsend.backend.dtos.response.AuthResponse;

@Service
public interface IEmailService {
	AuthResponse verifyEmail(String token);

}
