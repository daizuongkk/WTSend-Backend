package com.wtsend.backend.services.interfaces;

import com.wtsend.backend.dto.request.ChangePasswordRequest;
import com.wtsend.backend.dto.request.SignInRequest;
import com.wtsend.backend.dto.request.SignUpRequest;
import com.wtsend.backend.dto.response.AuthResponse;
import com.wtsend.backend.dto.response.UserResponse;

public interface IAuthService {

	AuthResponse signIn(SignInRequest req);

	AuthResponse googleLogin(String token);

	UserResponse signUp(SignUpRequest req);

	AuthResponse refreshToken(String refreshToken);

	String signOut(String refreshToken);

	void changePassword(ChangePasswordRequest request, String userId);

}
