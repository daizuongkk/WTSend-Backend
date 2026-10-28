package com.wtsend.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
  private UUID id;
  private String username;

  private String displayName;

  private String birthday;

  private String email;
  private boolean emailVerified;

  private String phone;
  private boolean phoneVerified;
  private boolean showOnlineStatus;
  private String avatarUrl;

  private String avatarId;

  private String bio;

  private Instant createdAt;
  private Instant updatedAt;
}
