package com.wtsend.backend.services;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.dto.request.SendMessageRequest;
import com.wtsend.backend.dto.response.MessageResponse;
import com.wtsend.backend.model.Conversation;
import com.wtsend.backend.model.User;
import com.wtsend.backend.model.enums.ConversationType;
import com.wtsend.backend.repository.ConversationRepository;
import com.wtsend.backend.repository.MessageRepository;
import com.wtsend.backend.repository.ParticipantRepository;
import com.wtsend.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MessageServiceV2 {

  private final MessageRepository messageRepository;
  private final UserRepository userRepository;
  private final ParticipantRepository participantRepository;
  private final ConversationRepository conversationRepository;

  @Retryable(retryFor = { CannotAcquireLockException.class,
      PessimisticLockingFailureException.class,
      // Losing the direct_key race: a retry re-reads and finds the winner.
      // Retry advice sits outside the transaction advice, so each attempt
      // gets a fresh transaction rather than reusing a rollback-only one.
      DataIntegrityViolationException.class }, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
  public MessageResponse send(SendMessageRequest request, String senderId) {

    User sender = userRepository.findById(senderId)
        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + senderId));

    Long conversationId = request.conversationId();

    Conversation conversation = conversationRepository.findByIdWithGraph(conversationId)
        .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND).withDetail("id=" + conversationId));

    if (!conversation.getType().equals(ConversationType.GROUP))
      throw new AppException(ErrorCode.CONVERSATION_INVALID_TYPE);

    return MessageResponse.builder().build();
  }

  private MessageResponse sendDirectMessage() {
    return MessageResponse.builder().build();
  }

}
