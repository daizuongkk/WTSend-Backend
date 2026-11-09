package com.wtsend.backend.dto.request;

import com.wtsend.backend.dto.MediaRequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SendDirectMessageRequest {

  @NotBlank(message = "Recipient id is required")
  private String recipientId;

  // Null on the first message to a friend: the client has no conversation yet.
  // MessageService resolves or creates it.
  private Long conversationId;

  /**
   * Bounded by messages.content, a MySQL TEXT: 65,535 bytes, which under
   * utf8mb4 is as few as 16,383 characters. Past that the insert failed with a
   * 500 rather than a 400.
   */
  @Size(max = 16000, message = "content is too long")
  private String content;

  private MediaRequest mediaRequest;

}
