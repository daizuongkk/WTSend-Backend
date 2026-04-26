package com.wtsend.backend.services.interfaces;

import org.springframework.stereotype.Service;

import com.wtsend.backend.dtos.request.SignInRequest;
import com.wtsend.backend.dtos.request.SignUpRequest;
import com.wtsend.backend.dtos.response.AuthResponse;

@Service
public interface IAuthService {
	AuthResponse signIn(SignInRequest req);

	AuthResponse signUp(SignUpRequest req);

	AuthResponse refreshToken(String refreshToken);

	AuthResponse signOut(String refreshToken);
}
