package com.wtsend.backend.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.corundumstudio.socketio.SocketIOServer;
import com.wtsend.backend.dtos.request.CreateConversationRequest;
import com.wtsend.backend.dtos.response.ConversationResponse;
import com.wtsend.backend.exceptions.ForbiddenException;
import com.wtsend.backend.exceptions.RequestException;
import com.wtsend.backend.exceptions.ResourceNotFoundException;
import com.wtsend.backend.libs.ConversationMapper;
import com.wtsend.backend.libs.MessageMapper;
import com.wtsend.backend.models.Conversation;
import com.wtsend.backend.models.GroupInfo;
import com.wtsend.backend.models.Participant;
import com.wtsend.backend.models.User;
import com.wtsend.backend.models.enums.ConversationType;
import com.wtsend.backend.repositories.ConversationRepository;
import com.wtsend.backend.repositories.FriendRepository;
import com.wtsend.backend.repositories.ParticipantRepository;
import com.wtsend.backend.repositories.UserRepository;
import com.wtsend.backend.services.interfaces.IConversationService;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
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
	private final SocketIOServer server;

	@Override
	public List<Long> getConversationIdsByUserId(String userId) {
		return conversationRepo.getConversationId(userId);
	}

	@Override
	public List<ConversationResponse> getConversations(String userId) {
		List<Conversation> conversations = conversationRepo.findConversations(userId);

		return conversations.stream()
				.map(conversationMapper::toResponse).toList();
	}

	@Override
	public ConversationResponse createConversation(CreateConversationRequest request, String userId) {
		ConversationType type = request.getType();
		String name = request.getName();
		List<String> memberIds = request.getMemberIds();

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found by id: " + userId));

		for (String memberId : memberIds) {
			if (!friendRepo.existsFriendship(userId, memberId))
				throw new ForbiddenException("Cannot create conversations with strangers");
		}

		Conversation conversation = type.equals(ConversationType.DIRECT) ? createDirectConversation(user, memberIds)
				: createGroupConversation(user, memberIds, name);
		conversationRepo.save(conversation);
		if (type.equals(ConversationType.GROUP))
			memberIds.forEach(
					id -> server.getRoomOperations(id).sendEvent("new-group", conversationMapper.toResponse(conversation)));

		return conversationMapper.toResponse(conversation);

	}

	@Override
	@Transactional
	public void markAsSeen(Long conversationId, String userId) {
		Conversation conversation = conversationRepo.findById(conversationId)
				.orElseThrow(() -> new EntityNotFoundException("Conversation not found with id: " + conversationId));

		if (conversation.getLastMessage() == null)
			return;

		if (conversation.getLastMessage().getSender().getId().equals(userId))
			return;

		Participant participant = participantRepo.findByConversationIdAndUserId(conversation.getId(), userId)
				.orElseThrow(() -> new EntityNotFoundException(
						"Participant not found with conversation id: " + conversationId + " and user id: " + userId));

		participant.setLastSeenMessage(conversation.getLastMessage());
		participant.setLastReadAt(Instant.now());
		participant.setUnreadCounts(0L);

		participantRepo.save(participant);

		server.getRoomOperations(conversationId.toString()).sendEvent("read-message",
				Map.of("conversation", conversationMapper.toResponse(conversation), "lastMessage",
						messageMapper.toLastMessage(conversation.getLastMessage())));

	}

	private Conversation createNewDirectConversation(User sender, User recipient) {
		Conversation convo = new Conversation();
		convo.setType(ConversationType.DIRECT);

		Participant p1 = createParticipant(sender, convo);

		Participant p2 = createParticipant(recipient, convo);

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
			throw new RequestException("Cannot create conversation with yourself");
		}

		User participant = userRepository.findById(participantId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found by id: " + participantId));

		return conversationRepo
				.findDirectConversationByUsers(participantId, user.getId(), ConversationType.DIRECT)
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
		GroupInfo groupInfo = GroupInfo.builder().conversation(conversation).avatarUrl(name).name(name).creator(user)
				.build();
		conversation.setGroup(groupInfo);

		return conversation;
	}

}
