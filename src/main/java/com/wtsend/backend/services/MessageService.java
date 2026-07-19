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

import com.corundumstudio.socketio.SocketIOServer;
import com.wtsend.backend.dto.request.SendDirectMessageRequest;
import com.wtsend.backend.dto.request.SendGroupMessageRequest;
import com.wtsend.backend.dto.response.MessageResponse;
import com.wtsend.backend.dto.response.MessagesResponse;
import com.wtsend.backend.event.NewMessageEvent;
import com.wtsend.backend.exceptions.ForbiddenException;
import com.wtsend.backend.exceptions.RequestException;
import com.wtsend.backend.exceptions.ResourceNotFoundException;
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
	SocketIOServer server;
	ParticipantRepository participantRepo;
	ApplicationEventPublisher applicationEventPublisher;

	@Override
	public void sendDirectMessage(SendDirectMessageRequest request, String senderId) {

		String recipientId = request.getRecipientId();

		if (!friendRepo.existsFriendship(senderId, recipientId))
			throw new ForbiddenException("Can't send messages because not friends yet");

		String content = request.getContent();
		Long conversationId = request.getConversationId();

		User recipient = userRepository.findById(recipientId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found by id: " + recipientId));

		User sender = userRepository.findById(senderId)
				.orElseThrow(() -> new ResourceNotFoundException("Usre not found by id: " + senderId));

		Conversation conversation = conversationRepo.findById(conversationId)
				.orElseGet(() -> createNewDirectConversation(sender, recipient));

		Message message = new Message();
		message.setContent(content);
		message.setConversation(conversation);
		message.setSender(sender);
		messageRepo.save(message);
		MessageHelper.updateConversationAfterCreateMessage(conversation, message, senderId);
		conversationRepo.save(conversation);

		MessageHelper.emitNewMessage(server, conversationMapper.toResponse(conversation),
				messageMapper.toResponse(message));
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

	@Retryable(retryFor = { CannotAcquireLockException.class,
			DeadlockLoserDataAccessException.class }, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
	public void sendGroupMessage(SendGroupMessageRequest request, String senderId) {
		Long conversationId = request.getConversationId();

		Conversation conversation = conversationRepo.findById(conversationId)
				.orElseThrow(() -> new ResourceNotFoundException("Not found conversation with id: " + conversationId));

		if (!conversation.getType().equals(ConversationType.GROUP))
			throw new RequestException("Conversation is invalid");

		User sender = userRepository.findById(senderId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found by id: " + senderId));

		MessageHelper.checkMembership(conversation, sender);

		Message message = Message.builder().sender(sender).conversation(conversation).content(request.getContent()).build();
		messageRepo.save(message);

		conversationRepo.updateLastMessage(conversationId, message.getCreatedAt(), message.getId());
		participantRepo.incrementUnreadCountsForOthers(conversationId, senderId);
		participantRepo.resetUnreadCountForSender(conversationId, senderId);

		applicationEventPublisher.publishEvent(new NewMessageEvent(conversation, message));
	}

	@Override
	public MessagesResponse getMessages(Long conversationId, int limit, Instant cursor) {

		List<Message> messages;
		Pageable pageable = PageRequest.of(0, limit + 1);
		if (cursor != null) {
			messages = messageRepo.findAllByConversation_IdAndCreatedAtBeforeOrderByCreatedAtDesc(
					conversationId,
					cursor, PageRequest.of(0, limit + 1));
		} else {
			messages = messageRepo.findByConversation_IdOrderByCreatedAtDesc(conversationId, pageable);
		}

		Instant nextCursor = null;

		if (messages.size() > limit) {
			Message nextMessage = messages.getLast();
			nextCursor = nextMessage.getCreatedAt();
			messages.remove(messages.size() - 1);
		}

		List<MessageResponse> messageResponses = messages.reversed().stream()
				.map(messageMapper::toResponse)
				.toList();

		return MessagesResponse.builder().messages(messageResponses).nextCursor(nextCursor).build();

	}

}
