package com.wtsend.backend.services.interfaces;

import java.util.UUID;

import com.wtsend.backend.dto.response.AuthResponse;
import com.wtsend.backend.model.User;

public interface IEmailService {
  AuthResponse verifyEmail(String token);

  void sendVerifyLink(User user);

  /** Resolves the user itself, so callers don't need repository access. */
  void sendVerifyLink(UUID userId);

  void sendPasswordResetLink(User user);

  /**
   * Validates and consumes a reset token, returning its owner. Setting the
   * password is the caller's job -- this service owns tokens and mail.
   */
  User consumePasswordResetToken(String token);

}
