package com.wtsend.backend.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wtsend.backend.dto.request.SendDirectMessageRequest;
import com.wtsend.backend.dto.request.SendGroupMessageRequest;
import com.wtsend.backend.dto.response.ConversationResponse;
import com.wtsend.backend.dto.response.LastMessage;
import com.wtsend.backend.dto.response.MessageResponse;
import com.wtsend.backend.dto.response.MessagesResponse;
import com.wtsend.backend.event.NewMessageEvent;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.libs.ConversationMapper;
import com.wtsend.backend.libs.MessageCursor;
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
			DeadlockLoserDataAccessException.class,
			// Losing the direct_key race: a retry re-reads and finds the winner.
			// Retry advice sits outside the transaction advice, so each attempt
			// gets a fresh transaction rather than reusing a rollback-only one.
			DataIntegrityViolationException.class }, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
	public MessageResponse sendDirectMessage(SendDirectMessageRequest request, String senderId) {

		String recipientId = request.getRecipientId();

		if (!friendRepo.existsFriendship(senderId, recipientId))
			throw new AppException(ErrorCode.NOT_FRIENDS).withDetail("recipientId=" + recipientId);

		User recipient = userRepository.findById(recipientId)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + recipientId));

		User sender = userRepository.findById(senderId)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + senderId));

		Long requestedId = request.getConversationId();

		Conversation conversation;
		if (requestedId == null) {
			// First message to this friend -- the client has no conversation id yet.
			// Look the pair's thread up before creating one: without this, a client
			// that lost its local state would start a second DIRECT conversation and
			// the two users would each be reading a different half of the thread.
			//
			// This check still loses a genuine race, so the unique index on
			// direct_key is the real arbiter: the loser's insert fails, @Retryable
			// runs the whole method again in a fresh transaction, and the lookup
			// then finds the winner's conversation.
			conversation = conversationRepo
					.findByDirectKey(Conversation.directKeyOf(senderId, recipientId))
					.orElseGet(() -> conversationRepo.save(createNewDirectConversation(sender, recipient)));
		} else {
			// The conversation id comes from the client and is not covered by the
			// friendship check above, so verify it really is this pair's thread.
			Conversation existing = conversationRepo.findById(requestedId)
					.orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND)
							.withDetail("id=" + requestedId));
			if (existing.getType() != ConversationType.DIRECT)
				throw new AppException(ErrorCode.CONVERSATION_INVALID_TYPE);
			MessageHelper.checkMembership(existing, sender);
			MessageHelper.checkMembership(existing, recipient);
			conversation = existing;
		}

		Message message = Message.builder().sender(sender).conversation(conversation)
				.content(request.getContent()).imgUrl(request.getImgUrl()).build();
		messageRepo.save(message);

		return publishNewMessage(conversation, message, senderId);
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

		convo.setDirectKey(Conversation.directKeyOf(sender.getId(), recipient.getId()));
		convo.setParticipants(new ArrayList<>(List.of(p1, p2)));
		convo.setLastMessageAt(Instant.now());

		return convo;
	}

	@Override
	@Retryable(retryFor = { CannotAcquireLockException.class,
			DeadlockLoserDataAccessException.class }, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
	public MessageResponse sendGroupMessage(SendGroupMessageRequest request, String senderId) {
		Long conversationId = request.getConversationId();

		Conversation conversation = conversationRepo.findById(conversationId)
				.orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND).withDetail("id=" + conversationId));

		if (!conversation.getType().equals(ConversationType.GROUP))
			throw new AppException(ErrorCode.CONVERSATION_INVALID_TYPE);

		User sender = userRepository.findById(senderId)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + senderId));

		MessageHelper.checkMembership(conversation, sender);

		Message message = Message.builder().sender(sender).conversation(conversation).content(request.getContent())
				.imgUrl(request.getImgUrl()).build();
		messageRepo.save(message);

		return publishNewMessage(conversation, message, senderId);
	}

	/**
	 * Applies the counter updates as atomic SQL, then maps the conversation from
	 * a fresh read and hands the DTOs to the AFTER_COMMIT listener.
	 *
	 * <p>
	 * The order matters and used to be the other way round. Mapping first meant
	 * {@code ParticipantResponse.unreadCounts} was always one increment behind --
	 * permanently, for every message: the recipient's {@code new-message} payload
	 * said 0 when the row said 1, and said 9 on the tenth message. A client that
	 * trusted the payload showed no badge until the next full reload.
	 *
	 * <p>
	 * The message DTOs are still mapped first, because the bulk updates below
	 * clear the persistence context and {@code message} would be detached with
	 * lazy associations after them.
	 */
	private MessageResponse publishNewMessage(Conversation conversation, Message message, String senderId) {
		MessageResponse messageDto = messageMapper.toResponse(message);
		LastMessage lastMessageDto = messageMapper.toLastMessage(message);
		Instant sentAt = message.getCreatedAt();

		Long conversationId = conversation.getId();
		conversationRepo.updateLastMessage(conversationId, sentAt, message.getId());
		participantRepo.incrementUnreadCountsForOthers(conversationId, senderId);
		participantRepo.resetUnreadCountForSender(conversationId, senderId);

		// Re-read after the counters, with the graph the mapper walks.
		ConversationResponse conversationDto = conversationMapper.toResponse(
				conversationRepo.findByIdWithGraph(conversationId)
						.orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND)
								.withDetail("id=" + conversationId)));
		conversationDto.setLastMessage(lastMessageDto);
		conversationDto.setLastMessageAt(sentAt);

		applicationEventPublisher.publishEvent(new NewMessageEvent(conversationDto, messageDto));

		// Returned to the sender so the client can swap its optimistic bubble for the
		// real row. Without an id it had to guess which echo belonged to which send.
		return messageDto;
	}

	@Override
	@Transactional(readOnly = true)
	public MessagesResponse getMessages(Long conversationId, String userId, int limit, String cursor) {

		if (participantRepo.findByConversationIdAndUserId(conversationId, userId).isEmpty())
			throw new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED)
					.withDetail("conversationId=" + conversationId + " userId=" + userId);

		MessageCursor position = MessageCursor.parse(cursor);
		Pageable pageable = PageRequest.of(0, limit + 1);
		List<Message> messages = position != null
				? messageRepo.findPageOlderThan(conversationId, position.createdAt(), position.id(), pageable)
				: messageRepo.findNewestPage(conversationId, pageable);

		String nextCursor = null;

		if (messages.size() > limit) {
			// Drop the probe row, then take the cursor from the last row we KEPT --
			// the next page queries strictly before the cursor, so using the probe
			// row would skip it permanently.
			messages = new ArrayList<>(messages.subList(0, limit));
			Message oldestKept = messages.get(limit - 1);
			nextCursor = new MessageCursor(oldestKept.getCreatedAt(), oldestKept.getId()).encode();
		}

		List<MessageResponse> messageResponses = messages.reversed().stream()
				.map(messageMapper::toResponse)
				.toList();

		return MessagesResponse.builder().messages(messageResponses).nextCursor(nextCursor).build();
	}

}
