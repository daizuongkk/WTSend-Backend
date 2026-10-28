package com.wtsend.backend.services.interfaces;

import java.util.UUID;

import com.wtsend.backend.dto.request.ChangePasswordRequest;
import com.wtsend.backend.dto.request.FacebookAuthRequest;
import com.wtsend.backend.dto.request.ForgotPasswordRequest;
import com.wtsend.backend.dto.request.ResetPasswordRequest;
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

  /**
   * Revokes every other session and returns a fresh pair for the caller's own
   * device, which has just proved it knows the current password.
   */
  AuthResponse changePassword(ChangePasswordRequest request, UUID userId);

  /** Sends a reset link. Silent for an unknown address, by design. */
  void forgotPassword(ForgotPasswordRequest request);

  void resetPassword(ResetPasswordRequest request);

  /** Signs in through Facebook, creating the account on first use. */
  AuthResponse facebookLogin(FacebookAuthRequest request);

}
