package com.wtsend.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.wtsend.backend.dto.request.SendDirectMessageRequest;
import com.wtsend.backend.dto.response.ConversationResponse;
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

		// publishNewMessage re-reads the conversation AFTER the counter SQL, so
		// the broadcast carries post-increment unread counts. Lenient because the
		// read paths in this class never reach it.
		lenient().when(conversationRepo.findByIdWithGraph(anyLong()))
				.thenAnswer(inv -> Optional.of(directConversationOf(alice, bob)));
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

		assertThatThrownBy(() -> messageService.getMessages(CONVO_ID, MALLORY, 50, (String) null))
				.isInstanceOf(AppException.class)
				.extracting(ex -> ((AppException) ex).getErrorCode())
				.isEqualTo(ErrorCode.CONVERSATION_ACCESS_DENIED);

		verify(messageRepo, never()).findNewestPage(anyLong(), any());
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
	void sendDirectMessageWithoutAConversationIdStartsTheThread() {
		// The very first message to a new friend: the client has no conversation id
		// to send. @NotNull on the DTO used to reject this outright, which made the
		// create-conversation branch below unreachable.
		when(friendRepo.existsFriendship(ALICE, BOB)).thenReturn(true);
		when(userRepository.findById(BOB)).thenReturn(Optional.of(bob));
		when(userRepository.findById(ALICE)).thenReturn(Optional.of(alice));
		when(conversationRepo.findByDirectKey(Conversation.directKeyOf(ALICE, BOB)))
				.thenReturn(Optional.empty());
		// Hand back a row WITH an id, the way the database does -- the service
		// re-reads the conversation by that id to build the broadcast payload.
		when(conversationRepo.save(any(Conversation.class))).thenAnswer(inv -> {
			Conversation saved = inv.getArgument(0);
			saved.setId(CONVO_ID);
			return saved;
		});
		when(conversationMapper.toResponse(any())).thenReturn(new ConversationResponse());
		when(messageMapper.toResponse(any())).thenReturn(new MessageResponse());

		messageService.sendDirectMessage(directRequest(BOB, null, "hi"), ALICE);

		verify(conversationRepo).save(any(Conversation.class));
		verify(messageRepo).save(any(Message.class));
	}

	@Test
	void sendDirectMessageWithoutAConversationIdReusesThePairsThread() {
		// A client that lost its local state must not start a second DIRECT thread --
		// the two users would each end up reading a different half of the history.
		when(friendRepo.existsFriendship(ALICE, BOB)).thenReturn(true);
		when(userRepository.findById(BOB)).thenReturn(Optional.of(bob));
		when(userRepository.findById(ALICE)).thenReturn(Optional.of(alice));
		when(conversationRepo.findByDirectKey(Conversation.directKeyOf(ALICE, BOB)))
				.thenReturn(Optional.of(directConversationOf(alice, bob)));
		when(conversationMapper.toResponse(any())).thenReturn(new ConversationResponse());
		when(messageMapper.toResponse(any())).thenReturn(new MessageResponse());

		messageService.sendDirectMessage(directRequest(BOB, null, "hi again"), ALICE);

		verify(conversationRepo, never()).save(any(Conversation.class));
		verify(messageRepo).save(any(Message.class));
	}

	@Test
	void sendDirectMessageRejectsAnUnknownConversationId() {
		// Previously an id that matched nothing fell through to the create branch and
		// silently opened a new conversation instead of reporting the bad id.
		when(friendRepo.existsFriendship(ALICE, BOB)).thenReturn(true);
		when(userRepository.findById(BOB)).thenReturn(Optional.of(bob));
		when(userRepository.findById(ALICE)).thenReturn(Optional.of(alice));
		when(conversationRepo.findById(CONVO_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> messageService.sendDirectMessage(directRequest(BOB, CONVO_ID, "hi"), ALICE))
				.isInstanceOf(AppException.class)
				.extracting(ex -> ((AppException) ex).getErrorCode())
				.isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND);

		verify(conversationRepo, never()).save(any(Conversation.class));
		verify(messageRepo, never()).save(any());
	}

	@Test
	void sendDirectMessagePersistsTheAttachedImage() {
		// imgUrl was accepted and validated by the DTO but never read, so
		// Message.imgUrl stayed null and image messages arrived empty.
		when(friendRepo.existsFriendship(ALICE, BOB)).thenReturn(true);
		when(userRepository.findById(BOB)).thenReturn(Optional.of(bob));
		when(userRepository.findById(ALICE)).thenReturn(Optional.of(alice));
		when(conversationRepo.findById(CONVO_ID)).thenReturn(Optional.of(directConversationOf(alice, bob)));
		when(conversationMapper.toResponse(any())).thenReturn(new ConversationResponse());
		when(messageMapper.toResponse(any())).thenReturn(new MessageResponse());

		SendDirectMessageRequest request = directRequest(BOB, CONVO_ID, "look");
		ReflectionTestUtils.setField(request, "imgUrl", "https://res.cloudinary.com/x.png");

		messageService.sendDirectMessage(request, ALICE);

		ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
		verify(messageRepo).save(saved.capture());
		assertThat(saved.getValue().getImgUrl()).isEqualTo("https://res.cloudinary.com/x.png");
	}

	@Test
	void cursorPaginationReturnsEveryMessageAcrossPages() {
		// 7 messages, 3 per page. An earlier cursor bug took the probe row as the
		// cursor and then queried strictly before it, dropping one per page.
		List<Message> history = history(7, i -> Instant.ofEpochSecond(1000 + i));

		stubCursorQueries(history);

		assertThat(walkEveryPage(3)).containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L, 6L);
	}

	@Test
	void cursorPaginationHandlesMessagesSharingATimestamp() {
		// Six messages at only two distinct timestamps, so a page boundary is
		// guaranteed to land on a tie. Ordering by createdAt alone is not a total
		// order: "createdAt < cursor" skipped every message tied with the boundary
		// row, and the client silently lost them.
		List<Message> history = history(6, i -> Instant.ofEpochSecond(1000 + i / 3));

		stubCursorQueries(history);

		List<Long> seen = walkEveryPage(2);
		assertThat(seen).containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 4L, 5L);
		assertThat(seen).doesNotHaveDuplicates();
	}

	@Test
	void anEmptyCursorIsTreatedAsTheFirstPage() {
		// The client sends cursor= for the first page. The parameter used to be
		// bound as an Instant, so an empty value failed conversion before the
		// controller method ran and the first page always came back 400.
		stubCursorQueries(history(2, i -> Instant.ofEpochSecond(1000 + i)));

		MessagesResponse res = messageService.getMessages(CONVO_ID, ALICE, 10, "");

		assertThat(res.getMessages()).hasSize(2);
		assertThat(res.getNextCursor()).isNull();
	}

	@Test
	void aMalformedCursorIsRejected() {
		when(participantRepo.findByConversationIdAndUserId(CONVO_ID, ALICE))
				.thenReturn(Optional.of(new Participant()));

		assertThatThrownBy(() -> messageService.getMessages(CONVO_ID, ALICE, 10, "not-a-cursor"))
				.isInstanceOf(AppException.class)
				.extracting(ex -> ((AppException) ex).getErrorCode())
				.isEqualTo(ErrorCode.MALFORMED_REQUEST);
	}

	/** Messages 0..n-1, each timestamped by the supplied function. */
	private static List<Message> history(int count, java.util.function.IntFunction<Instant> timestampOf) {
		List<Message> messages = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Message m = new Message();
			m.setId((long) i);
			m.setContent("m" + i);
			m.setCreatedAt(timestampOf.apply(i));
			messages.add(m);
		}
		return messages;
	}

	private void stubCursorQueries(List<Message> history) {
		// Newest first, ties broken by descending id -- the repository's ORDER BY.
		List<Message> ordered = new ArrayList<>(history);
		ordered.sort(java.util.Comparator.comparing(Message::getCreatedAt)
				.thenComparing(Message::getId).reversed());

		when(participantRepo.findByConversationIdAndUserId(eq(CONVO_ID), eq(ALICE)))
				.thenReturn(Optional.of(new Participant()));
		when(messageMapper.toResponse(any(Message.class))).thenAnswer(inv -> {
			MessageResponse r = new MessageResponse();
			r.setId(((Message) inv.getArgument(0)).getId());
			return r;
		});
		when(messageRepo.findNewestPage(eq(CONVO_ID), any(Pageable.class)))
				.thenAnswer(inv -> page(ordered, null, null, inv.getArgument(1, Pageable.class).getPageSize()));
		when(messageRepo.findPageOlderThan(eq(CONVO_ID), any(Instant.class), anyLong(), any(Pageable.class)))
				.thenAnswer(inv -> page(ordered, inv.getArgument(1, Instant.class), inv.getArgument(2, Long.class),
						inv.getArgument(3, Pageable.class).getPageSize()));
	}

	/** Pages through the whole conversation and collects every id returned. */
	private List<Long> walkEveryPage(int pageSize) {
		List<Long> seen = new ArrayList<>();
		String cursor = null;
		for (int guard = 0; guard < 20; guard++) {
			MessagesResponse res = messageService.getMessages(CONVO_ID, ALICE, pageSize, cursor);
			res.getMessages().forEach(m -> seen.add(m.getId()));
			cursor = res.getNextCursor();
			if (cursor == null)
				break;
		}
		return seen;
	}

	/** Mimics the repository: the (createdAt, id) keyset comparison, limited. */
	private static List<Message> page(List<Message> ordered, Instant beforeTimestamp, Long beforeId, int size) {
		return new ArrayList<>(ordered.stream()
				.filter(m -> beforeTimestamp == null
						|| m.getCreatedAt().isBefore(beforeTimestamp)
						|| (m.getCreatedAt().equals(beforeTimestamp) && m.getId() < beforeId))
				.limit(size)
				.toList());
	}
}
