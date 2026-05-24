package com.wtsend.backend.services.interfaces;

import org.springframework.stereotype.Service;

import com.wtsend.backend.dtos.request.ChangePasswordRequest;
import com.wtsend.backend.dtos.request.SignInRequest;
import com.wtsend.backend.dtos.request.SignUpRequest;
import com.wtsend.backend.dtos.response.AuthResponse;
import com.wtsend.backend.dtos.response.UserResponse;

@Service
public interface IAuthService {

	AuthResponse signIn(SignInRequest req);

	AuthResponse googleLogin(String provider, String token);

	UserResponse signUp(SignUpRequest req);

	AuthResponse refreshToken(String refreshToken);

	String signOut(String refreshToken);

	void changePassword(ChangePasswordRequest request);

}
