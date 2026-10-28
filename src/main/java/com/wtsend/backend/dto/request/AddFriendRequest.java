package com.wtsend.backend.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddFriendRequest {

  @NotBlank(message = "Recipient Id is required")
  private UUID to;

  /** Bounded by friend_request.message. */
  @Size(max = 255, message = "message must be at most 255 characters")
  private String message;
}
