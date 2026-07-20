package com.wtsend.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.wtsend.backend.dto.request.SendDirectMessageRequest;
import com.wtsend.backend.dto.response.MessageResponse;
import com.wtsend.backend.dto.response.MessagesResponse;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.libs.ConversationMapper;
import com.wtsend.backend.libs.MessageMapper;
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

/**
 * Guards the authorization holes fixed on this branch: any authenticated user
 * could previously read any conversation and write into a stranger's thread.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageServiceTest {

	private static final String ALICE = "alice-id";
	private static final String BOB = "bob-id";
	private static final String MALLORY = "mallory-id";
	private static final Long CONVO_ID = 42L;

	@Mock
	private ConversationMapper conversationMapper;
	@Mock
	private UserRepository userRepository;
	@Mock
	private ConversationRepository conversationRepo;
	@Mock
	private MessageRepository messageRepo;
	@Mock
	private FriendRepository friendRepo;
	@Mock
	private MessageMapper messageMapper;
	@Mock
	private ParticipantRepository participantRepo;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private MessageService messageService;

	private User alice;
	private User bob;
	private User mallory;

	@BeforeEach
	void setUp() {
		alice = user(ALICE);
		bob = user(BOB);
		mallory = user(MALLORY);
	}

	private static User user(String id) {
		User u = new User();
		u.setId(id);
		return u;
	}

	/** The request DTO is deserialized by field access and exposes no setters. */
	private static SendDirectMessageRequest directRequest(String recipientId, Long conversationId, String content) {
		SendDirectMessageRequest r = new SendDirectMessageRequest();
		ReflectionTestUtils.setField(r, "recipientId", recipientId);
		ReflectionTestUtils.setField(r, "conversationId", conversationId);
		ReflectionTestUtils.setField(r, "content", content);
		return r;
	}

	private static Conversation directConversationOf(User... members) {
		Conversation c = new Conversation();
		c.setId(CONVO_ID);
		c.setType(ConversationType.DIRECT);
		List<Participant> participants = new ArrayList<>();
		for (User u : members) {
			Participant p = new Participant();
			p.setUser(u);
			p.setConversation(c);
			participants.add(p);
		}
		c.setParticipants(participants);
		return c;
	}

	@Test
	void getMessagesRejectsANonParticipant() {
		when(participantRepo.findByConversationIdAndUserId(CONVO_ID, MALLORY)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> messageService.getMessages(CONVO_ID, MALLORY, 50, null))
				.isInstanceOf(AppException.class)
				.extracting(ex -> ((AppException) ex).getErrorCode())
				.isEqualTo(ErrorCode.CONVERSATION_ACCESS_DENIED);

		verify(messageRepo, never()).findByConversation_IdOrderByCreatedAtDesc(anyLong(), any());
	}

	@Test
	void sendDirectMessageRejectsAConversationTheSenderIsNotIn() {
		// Mallory is friends with Bob, so the friendship check passes -- but the
		// conversation id she supplies belongs to Alice and Bob.
		when(friendRepo.existsFriendship(MALLORY, BOB)).thenReturn(true);
		when(userRepository.findById(BOB)).thenReturn(Optional.of(bob));
		when(userRepository.findById(MALLORY)).thenReturn(Optional.of(mallory));
		when(conversationRepo.findById(CONVO_ID)).thenReturn(Optional.of(directConversationOf(alice, bob)));

		SendDirectMessageRequest request = directRequest(BOB, CONVO_ID, "injected");

		assertThatThrownBy(() -> messageService.sendDirectMessage(request, MALLORY))
				.isInstanceOf(AppException.class)
				.extracting(ex -> ((AppException) ex).getErrorCode())
				.isEqualTo(ErrorCode.CONVERSATION_ACCESS_DENIED);

		verify(messageRepo, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void sendDirectMessageAllowsAParticipant() {
		when(friendRepo.existsFriendship(ALICE, BOB)).thenReturn(true);
		when(userRepository.findById(BOB)).thenReturn(Optional.of(bob));
		when(userRepository.findById(ALICE)).thenReturn(Optional.of(alice));
		when(conversationRepo.findById(CONVO_ID)).thenReturn(Optional.of(directConversationOf(alice, bob)));
		when(conversationMapper.toResponse(any())).thenReturn(new com.wtsend.backend.dto.response.ConversationResponse());
		when(messageMapper.toResponse(any())).thenReturn(new MessageResponse());

		SendDirectMessageRequest request = directRequest(BOB, CONVO_ID, "hello");

		messageService.sendDirectMessage(request, ALICE);

		verify(messageRepo).save(any(Message.class));
	}

	@Test
	void cursorPaginationReturnsEveryMessageAcrossPages() {
		// 7 messages, 3 per page. The old cursor logic took the probe row as the
		// cursor and then queried strictly before it, dropping one per page.
		List<Message> all = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			Message m = new Message();
			m.setId((long) i);
			m.setContent("m" + i);
			m.setCreatedAt(Instant.ofEpochSecond(1000 + i));
			all.add(m);
		}
		List<Message> newestFirst = new ArrayList<>(all);
		newestFirst.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

		when(participantRepo.findByConversationIdAndUserId(eq(CONVO_ID), eq(ALICE)))
				.thenReturn(Optional.of(new Participant()));
		when(messageMapper.toResponse(any(Message.class))).thenAnswer(inv -> {
			MessageResponse r = new MessageResponse();
			r.setId(((Message) inv.getArgument(0)).getId());
			return r;
		});

		when(messageRepo.findByConversation_IdOrderByCreatedAtDesc(eq(CONVO_ID), any(Pageable.class)))
				.thenAnswer(inv -> page(newestFirst, null, inv.getArgument(1, Pageable.class).getPageSize()));
		when(messageRepo.findAllByConversation_IdAndCreatedAtBeforeOrderByCreatedAtDesc(eq(CONVO_ID),
				any(Instant.class), any(Pageable.class)))
				.thenAnswer(inv -> page(newestFirst, inv.getArgument(1, Instant.class),
						inv.getArgument(2, Pageable.class).getPageSize()));

		List<Long> seen = new ArrayList<>();
		Instant cursor = null;
		for (int guard = 0; guard < 10; guard++) {
			MessagesResponse res = messageService.getMessages(CONVO_ID, ALICE, 3, cursor);
			res.getMessages().forEach(m -> seen.add(m.getId()));
			cursor = res.getNextCursor();
			if (cursor == null)
				break;
		}

		assertThat(seen).containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L, 6L);
	}

	/** Mimics the repository: newest-first, strictly before the cursor, limited. */
	private static List<Message> page(List<Message> newestFirst, Instant before, int size) {
		return new ArrayList<>(newestFirst.stream()
				.filter(m -> before == null || m.getCreatedAt().isBefore(before))
				.limit(size)
				.toList());
	}
}
