package com.wtsend.backend.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wtsend.backend.dto.request.CreateConversationRequest;
import com.wtsend.backend.dto.response.ConversationResponse;
import com.wtsend.backend.event.ConversationCreatedEvent;
import com.wtsend.backend.event.ConversationReadEvent;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.libs.ConversationMapper;
import com.wtsend.backend.libs.MessageMapper;
import com.wtsend.backend.model.Conversation;
import com.wtsend.backend.model.GroupInfo;
import com.wtsend.backend.model.Participant;
import com.wtsend.backend.model.User;
import com.wtsend.backend.model.enums.ConversationType;
import com.wtsend.backend.repository.ConversationRepository;
import com.wtsend.backend.repository.FriendRepository;
import com.wtsend.backend.repository.ParticipantRepository;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.interfaces.IConversationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConversationService implements IConversationService {

	private final MessageMapper messageMapper;
	private final ConversationRepository conversationRepo;
	private final UserRepository userRepository;
	private final FriendRepository friendRepo;
	private final ConversationMapper conversationMapper;
	private final ParticipantRepository participantRepo;
	private final ApplicationEventPublisher eventPublisher;

	@Override
	@Transactional(readOnly = true)
	public List<Long> getConversationIdsByUserId(String userId) {
		return conversationRepo.getConversationId(userId);
	}

	@Override
	// open-in-view is off, so the session must be held open here: the mapper
	// walks participants, lastMessage and group, all of which are now lazy.
	@Transactional(readOnly = true)
	public List<ConversationResponse> getConversations(String userId) {
		List<Conversation> conversations = conversationRepo.findConversations(userId);

		return conversations.stream()
				.map(conversationMapper::toResponse).toList();
	}

	@Override
	// Was unannotated, so the membership checks, the save and the broadcast were
	// three unrelated units of work. It also has to be transactional for the
	// AFTER_COMMIT listener to fire at all -- without a transaction Spring drops
	// the event silently, and new-group would simply stop being delivered.
	@Transactional
	public ConversationResponse createConversation(CreateConversationRequest request, String userId) {
		ConversationType type = request.getType();
		String name = request.getName();
		List<String> memberIds = request.getMemberIds();

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + userId));

		// One query for the whole member list rather than one per member.
		List<User> strangers = friendRepo.findNonFriendsAmong(userId, memberIds);
		if (!strangers.isEmpty())
			throw new AppException(ErrorCode.CANNOT_CONVERSE_WITH_STRANGER)
					.withDetail("memberIds=" + strangers.stream().map(User::getId).toList());

		Conversation conversation = type.equals(ConversationType.DIRECT) ? createDirectConversation(user, memberIds)
				: createGroupConversation(user, memberIds, name);
		conversationRepo.save(conversation);

		ConversationResponse response = conversationMapper.toResponse(conversation);

		if (type.equals(ConversationType.GROUP)) {
			// Every participant, creator included: memberIds leaves the creator out,
			// so their other tabs and devices never heard about the new group.
			List<String> recipients = conversation.getParticipants().stream()
					.map(participant -> participant.getUser().getId())
					.toList();
			eventPublisher.publishEvent(new ConversationCreatedEvent(response, recipients));
		}

		return response;
	}

	@Override
	@Transactional
	public void markAsSeen(Long conversationId, String userId) {
		// Fetch the whole graph up front: the response built below walks
		// participants, lastMessage and group, each of which is lazy now.
		Conversation conversation = conversationRepo.findByIdWithGraph(conversationId)
				.orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND).withDetail("id=" + conversationId));

		if (conversation.getLastMessage() == null)
			return;

		if (conversation.getLastMessage().getSender().getId().equals(userId))
			return;

		Participant participant = participantRepo.findByConversationIdAndUserId(conversation.getId(), userId)
				.orElseThrow(() -> new AppException(ErrorCode.PARTICIPANT_NOT_FOUND)
						.withDetail("conversationId=" + conversationId + " userId=" + userId));

		participant.setLastSeenMessage(conversation.getLastMessage());
		participant.setLastReadAt(Instant.now());
		participant.setUnreadCounts(0L);

		participantRepo.save(participant);

		// Mapped here, while the entities are attached; broadcast by the
		// AFTER_COMMIT listener. Emitting from inside this method told clients a
		// message had been read before the write was durable.
		eventPublisher.publishEvent(new ConversationReadEvent(
				conversationMapper.toResponse(conversation),
				messageMapper.toLastMessage(conversation.getLastMessage())));

	}

	private Conversation createNewDirectConversation(User sender, User recipient) {
		Conversation convo = new Conversation();
		convo.setType(ConversationType.DIRECT);

		Participant p1 = createParticipant(sender, convo);

		Participant p2 = createParticipant(recipient, convo);

		convo.setDirectKey(Conversation.directKeyOf(sender.getId(), recipient.getId()));
		convo.setParticipants(new ArrayList<>(List.of(p1, p2)));

		return convo;
	}

	private Participant createParticipant(User user, Conversation convo) {
		return Participant.builder()
				.user(user)
				.conversation(convo)
				.unreadCounts(0L)
				.joinedAt(Instant.now())
				.build();
	}

	private Conversation createDirectConversation(User user, List<String> memberIds) {
		String participantId = memberIds.get(0);

		if (participantId.equals(user.getId())) {
			throw new AppException(ErrorCode.CANNOT_CONVERSE_WITH_SELF);
		}

		User participant = userRepository.findById(participantId)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=" + participantId));

		return conversationRepo
				.findByDirectKey(Conversation.directKeyOf(user.getId(), participantId))
				.orElseGet(() -> createNewDirectConversation(user, participant));
	}

	private Conversation createGroupConversation(User user, List<String> memberIds, String name) {

		if (name == null || name.isBlank()) {
			name = "Nhóm của " + user.getDisplayName().split(" ")[0];
		}
		List<User> users = userRepository.findAllById(memberIds);

		List<Participant> participants = new ArrayList<>();
		Conversation conversation = new Conversation();
		conversation.setType(ConversationType.GROUP);
		for (User u : users) {
			Participant p = createParticipant(u, conversation);
			participants.add(p);
		}
		participants.add(createParticipant(user, conversation));
		conversation.setParticipants(participants);
		// No avatarUrl until a group picture is uploaded -- it used to be set to the
		// group name, so any client rendering it as an <img src> fetched garbage.
		// Clients fall back to a composite of the members' avatars.
		GroupInfo groupInfo = GroupInfo.builder().conversation(conversation).name(name).creator(user)
				.build();
		conversation.setGroup(groupInfo);

		return conversation;
	}

}
