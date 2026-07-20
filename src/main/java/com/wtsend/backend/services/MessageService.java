package com.wtsend.backend.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import com.wtsend.backend.dto.request.SendDirectMessageRequest;
import com.wtsend.backend.dto.request.SendGroupMessageRequest;
import com.wtsend.backend.dto.response.ConversationResponse;
import com.wtsend.backend.dto.response.MessageResponse;
import com.wtsend.backend.dto.response.MessagesResponse;
import com.wtsend.backend.event.NewMessageEvent;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.libs.ConversationMapper;
import com.wtsend.backend.libs.MessageMapper;
import com.wtsend.backend.libs.utils.MessageHelper;
import com.wtsend.backend.model.Conversation;
import com.wtsend.backend.model.Message;
import com.wtsend.backend.model.Participant;
import com.wtsend.backend.model.User;
import com.wtsend.backend.model.enums.ConversationType;
import com.wtsend.backend.repository.ConversationRepository;
import com.wtsend.backend.repository.FriendRepository;
import com.wtsend.backend.repository.MessageRepository;
import com.wtsend.backend.repository.ParticipantRepository;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.interfaces.IMessageService;

import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@Transactional
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageService implements IMessageService {

	ConversationMapper conversationMapper;
	UserRepository userRepository;
	ConversationRepository conversationRepo;
	MessageRepository messageRepo;
	FriendRepository friendRepo;
	MessageMapper messageMapper;
	ParticipantRepository participantRepo;
	ApplicationEventPublisher applicationEventPublisher;

	@Override
	@Retryable(retryFor = { CannotAcquireLockException.class,
			DeadlockLoserDataAccessException.class }, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
	public void sendDirectMessage(SendDirectMessageRequest request, String senderId) {

		String recipientId = request.getRecipientId();

		if (!friendRepo.existsFriendship(senderId, recipientId))
			throw new AppException(ErrorCode.NOT_FRIENDS).withDetail("recipientId=" + recipientId);

		User recipient = userRepository.findById(recipientId)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + recipientId));

		User sender = userRepository.findById(senderId)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + senderId));

		Conversation existing = conversationRepo.findById(request.getConversationId()).orElse(null);

		Conversation conversation;
		if (existing == null) {
			conversation = conversationRepo.save(createNewDirectConversation(sender, recipient));
		} else {
			// The conversation id comes from the client and is not covered by the
			// friendship check above, so verify it really is this pair's thread.
			if (existing.getType() != ConversationType.DIRECT)
				throw new AppException(ErrorCode.CONVERSATION_INVALID_TYPE);
			MessageHelper.checkMembership(existing, sender);
			MessageHelper.checkMembership(existing, recipient);
			conversation = existing;
		}

		Message message = Message.builder().sender(sender).conversation(conversation)
				.content(request.getContent()).build();
		messageRepo.save(message);

		publishNewMessage(conversation, message, senderId);
	}

	private Conversation createNewDirectConversation(User sender, User recipient) {
		Conversation convo = new Conversation();
		convo.setType(ConversationType.DIRECT);

		Participant p1 = new Participant();
		p1.setUser(sender);
		p1.setConversation(convo);
		p1.setUnreadCounts(0L);

		Participant p2 = new Participant();
		p2.setUser(recipient);
		p2.setConversation(convo);
		p2.setUnreadCounts(0L);

		convo.setParticipants(new ArrayList<>(List.of(p1, p2)));
		convo.setLastMessageAt(Instant.now());

		return convo;
	}

	@Override
	@Retryable(retryFor = { CannotAcquireLockException.class,
			DeadlockLoserDataAccessException.class }, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
	public void sendGroupMessage(SendGroupMessageRequest request, String senderId) {
		Long conversationId = request.getConversationId();

		Conversation conversation = conversationRepo.findById(conversationId)
				.orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND).withDetail("id=" + conversationId));

		if (!conversation.getType().equals(ConversationType.GROUP))
			throw new AppException(ErrorCode.CONVERSATION_INVALID_TYPE);

		User sender = userRepository.findById(senderId)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + senderId));

		MessageHelper.checkMembership(conversation, sender);

		Message message = Message.builder().sender(sender).conversation(conversation).content(request.getContent())
				.build();
		messageRepo.save(message);

		publishNewMessage(conversation, message, senderId);
	}

	/**
	 * Maps to DTOs while the entities are still attached, then applies the counter
	 * updates as atomic SQL and hands the DTOs to the AFTER_COMMIT listener. The
	 * bulk updates clear the persistence context, so mapping has to happen first.
	 */
	private void publishNewMessage(Conversation conversation, Message message, String senderId) {
		ConversationResponse conversationDto = conversationMapper.toResponse(conversation);
		conversationDto.setLastMessage(messageMapper.toLastMessage(message));
		conversationDto.setLastMessageAt(message.getCreatedAt());
		MessageResponse messageDto = messageMapper.toResponse(message);

		Long conversationId = conversation.getId();
		conversationRepo.updateLastMessage(conversationId, message.getCreatedAt(), message.getId());
		participantRepo.incrementUnreadCountsForOthers(conversationId, senderId);
		participantRepo.resetUnreadCountForSender(conversationId, senderId);

		applicationEventPublisher.publishEvent(new NewMessageEvent(conversationDto, messageDto));
	}

	@Override
	public MessagesResponse getMessages(Long conversationId, String userId, int limit, Instant cursor) {

		if (participantRepo.findByConversationIdAndUserId(conversationId, userId).isEmpty())
			throw new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED)
					.withDetail("conversationId=" + conversationId + " userId=" + userId);

		Pageable pageable = PageRequest.of(0, limit + 1);
		List<Message> messages = cursor != null
				? messageRepo.findAllByConversation_IdAndCreatedAtBeforeOrderByCreatedAtDesc(conversationId, cursor,
						pageable)
				: messageRepo.findByConversation_IdOrderByCreatedAtDesc(conversationId, pageable);

		Instant nextCursor = null;

		if (messages.size() > limit) {
			// Drop the probe row, then take the cursor from the last row we KEPT --
			// the next page queries strictly before the cursor, so using the probe
			// row would skip it permanently.
			messages = new ArrayList<>(messages.subList(0, limit));
			nextCursor = messages.get(limit - 1).getCreatedAt();
		}

		List<MessageResponse> messageResponses = messages.reversed().stream()
				.map(messageMapper::toResponse)
				.toList();

		return MessagesResponse.builder().messages(messageResponses).nextCursor(nextCursor).build();
	}

}
