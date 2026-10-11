package com.wtsend.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.dto.request.CreateConversationRequest;
import com.wtsend.backend.dto.response.ConversationResponse;
import com.wtsend.backend.event.ConversationCreatedEvent;
import com.wtsend.backend.libs.ConversationMapper;
import com.wtsend.backend.libs.MessageMapper;
import com.wtsend.backend.model.Conversation;
import com.wtsend.backend.model.User;
import com.wtsend.backend.model.enums.ConversationType;
import com.wtsend.backend.repository.ConversationRepository;
import com.wtsend.backend.repository.FriendRepository;
import com.wtsend.backend.repository.ParticipantRepository;
import com.wtsend.backend.repository.UserRepository;

/**
 * Who ends up in a conversation that was just created.
 *
 * <p>
 * The member list is client input and was trusted further than it should have
 * been: {@code findNonFriendsAmong} reports members who are not friends, so an
 * id matching no row at all produced no stranger and passed straight through,
 * and {@code findAllById} then quietly dropped it. The group came back 201 with
 * a member missing, and nobody was told which one.
 */
@ExtendWith(MockitoExtension.class)
class ConversationCreationTest {

	private static final String ALICE = "alice";
	private static final String BOB = "bob";

	@Mock
	private MessageMapper messageMapper;
	@Mock
	private ConversationRepository conversationRepo;
	@Mock
	private UserRepository userRepository;
	@Mock
	private FriendRepository friendRepo;
	@Mock
	private ConversationMapper conversationMapper;
	@Mock
	private ParticipantRepository participantRepo;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private ConversationService conversationService;

	private User alice;
	private User bob;

	@BeforeEach
	void setUp() {
		alice = user(ALICE, "Alice");
		bob = user(BOB, "Bob");
	}

	private static User user(String id, String displayName) {
		User user = new User();
		user.setId(id);
		user.setDisplayName(displayName);
		return user;
	}

	private static CreateConversationRequest request(ConversationType type, List<String> memberIds) {
		return CreateConversationRequest.builder().type(type).memberIds(memberIds).build();
	}

	/** Everybody on the list is a friend of Alice and exists. */
	private void aliceIsFriendsWith(List<User> members) {
		when(userRepository.findById(ALICE)).thenReturn(Optional.of(alice));
		when(friendRepo.findNonFriendsAmong(any(), any())).thenReturn(List.of());
		when(userRepository.findAllById(any())).thenReturn(members);
	}

	@Test
	void aMemberIdThatMatchesNoAccountIsReportedRatherThanDropped() {
		when(userRepository.findById(ALICE)).thenReturn(Optional.of(alice));
		when(friendRepo.findNonFriendsAmong(any(), any())).thenReturn(List.of());
		// Only Bob came back: "ghost" matches no row, and neither the stranger
		// check nor findAllById has anything to say about that.
		when(userRepository.findAllById(any())).thenReturn(List.of(bob));

		assertThatThrownBy(() -> conversationService.createConversation(
				request(ConversationType.GROUP, List.of(BOB, "ghost")), ALICE))
				.isInstanceOf(AppException.class)
				.extracting(e -> ((AppException) e).getErrorCode())
				.isEqualTo(ErrorCode.USER_NOT_FOUND);

		verify(conversationRepo, never()).save(any());
	}

	@Test
	void aDirectConversationWithMoreThanOneMemberIsRefused() {
		// memberIds.get(0) was taken and the rest discarded in silence, so the
		// caller got a DIRECT thread with one of the two people they asked for.
		assertThatThrownBy(() -> conversationService.createConversation(
				request(ConversationType.DIRECT, List.of(BOB, "carol")), ALICE))
				.isInstanceOf(AppException.class)
				.extracting(e -> ((AppException) e).getErrorCode())
				.isEqualTo(ErrorCode.CONVERSATION_INVALID_TYPE);

		verify(userRepository, never()).findById(any());
	}

	@Test
	void theSameMemberTwiceCountsOnce() {
		aliceIsFriendsWith(List.of(bob));
		when(conversationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(conversationMapper.toResponse(any())).thenReturn(new ConversationResponse());

		// Two Participant rows for one user would have failed on
		// uk_participants_conversation_user.
		conversationService.createConversation(request(ConversationType.GROUP, List.of(BOB, BOB)), ALICE);

		ArgumentCaptor<Conversation> saved = ArgumentCaptor.forClass(Conversation.class);
		verify(conversationRepo).save(saved.capture());
		assertThat(saved.getValue().getParticipants())
				.extracting(participant -> participant.getUser().getId())
				.containsExactlyInAnyOrder(ALICE, BOB);
	}

	@Test
	void aDirectConversationIsAnnouncedToo() {
		aliceIsFriendsWith(List.of(bob));
		when(conversationRepo.findByDirectKey(any())).thenReturn(Optional.empty());
		when(conversationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(conversationMapper.toResponse(any())).thenReturn(new ConversationResponse());

		conversationService.createConversation(request(ConversationType.DIRECT, List.of(BOB)), ALICE);

		// Only GROUP used to be published, so a DIRECT thread created and left
		// without a message reached the other person on their next full load and
		// not before: the first-message path that normally covers it had not run.
		verify(eventPublisher).publishEvent(any(ConversationCreatedEvent.class));
	}

	@Test
	void aGroupOfRealFriendsIsStillCreated() {
		aliceIsFriendsWith(List.of(bob));
		when(conversationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(conversationMapper.toResponse(any())).thenReturn(new ConversationResponse());

		conversationService.createConversation(request(ConversationType.GROUP, List.of(BOB)), ALICE);

		verify(conversationRepo).save(any());
		// Every participant hears about it, the creator included.
		verify(eventPublisher).publishEvent(any(ConversationCreatedEvent.class));
	}
}
