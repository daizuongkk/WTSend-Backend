package com.wtsend.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.corundumstudio.socketio.BroadcastOperations;
import com.corundumstudio.socketio.SocketIOServer;
import com.wtsend.backend.dto.request.SendDirectMessageRequest;
import com.wtsend.backend.dto.response.ConversationResponse;
import com.wtsend.backend.dto.response.MessageResponse;
import com.wtsend.backend.event.NewMessageEvent;
import com.wtsend.backend.model.Friend;
import com.wtsend.backend.model.Participant;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.ConversationRepository;
import com.wtsend.backend.repository.FriendRepository;
import com.wtsend.backend.repository.ParticipantRepository;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.interfaces.IConversationService;
import com.wtsend.backend.services.interfaces.IMessageService;

import tools.jackson.databind.ObjectMapper;

/**
 * The unread counter, from both ends: what the broadcast says it is, and what
 * survives a reader and a sender touching the same row.
 *
 * <p>
 * Both defects pinned here were about ORDER -- the DTO being built before the
 * counter SQL, and a load-mutate-save interleaving with an atomic increment --
 * so neither showed up in a single-threaded happy path, and the existing suite
 * passed through both.
 */
@SpringBootTest
class UnreadCountTest {

	/** Captures the payload as published, before any listener reshapes it. */
	@TestConfiguration
	static class EventCapture {
		final List<NewMessageEvent> events = new ArrayList<>();

		@EventListener
		void on(NewMessageEvent event) {
			events.add(event);
		}
	}

	/** Keeps the real server off port 9092 while this context is up. */
	@MockitoBean
	private SocketIOServer server;

	@Autowired
	private IMessageService messageService;
	@Autowired
	private IConversationService conversationService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ConversationRepository conversationRepo;
	@Autowired
	private ParticipantRepository participantRepo;
	@Autowired
	private FriendRepository friendRepo;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private ObjectMapper json;

	private TransactionTemplate transactions;
	private String senderId;
	private String readerId;
	private Long conversationId;
	private final List<String> createdUserIds = new ArrayList<>();
	private final List<Long> createdFriendIds = new ArrayList<>();

	@BeforeEach
	void setUp() {
		transactions = new TransactionTemplate(transactionManager);
		when(server.getRoomOperations(any())).thenReturn(Mockito.mock(BroadcastOperations.class));
		when(server.getBroadcastOperations()).thenReturn(Mockito.mock(BroadcastOperations.class));

		transactions.executeWithoutResult(status -> {
			User sender = userRepository.save(User.builder()
					.email("unread-sender-" + System.nanoTime() + "@example.com")
					.displayName("Sender").emailVerified(true).build());
			User reader = userRepository.save(User.builder()
					.email("unread-reader-" + System.nanoTime() + "@example.com")
					.displayName("Reader").emailVerified(true).build());
			senderId = sender.getId();
			readerId = reader.getId();
			createdUserIds.add(senderId);
			createdUserIds.add(readerId);

			// sendDirectMessage refuses non-friends, and the pair is stored in the
			// canonical (low, high) order the repository queries expect.
			User low = senderId.compareTo(readerId) <= 0 ? sender : reader;
			User high = low == sender ? reader : sender;
			createdFriendIds.add(friendRepo.save(Friend.builder().userA(low).userB(high).build()).getId());
		});
	}

	/**
	 * These tests must commit -- the counter SQL and the AFTER_COMMIT seam only
	 * mean anything against a real transaction -- so they clean up after
	 * themselves.
	 */
	@AfterEach
	void removeWhatThisTestCommitted() {
		transactions.executeWithoutResult(status -> {
			if (conversationId != null)
				conversationRepo.findByIdWithGraph(conversationId).ifPresent(conversation -> {
					conversation.setLastMessage(null);
					conversation.getParticipants().forEach(p -> p.setLastSeenMessage(null));
					conversationRepo.saveAndFlush(conversation);
					conversationRepo.delete(conversation);
				});
			conversationRepo.flush();
			createdFriendIds.forEach(friendRepo::deleteById);
			createdUserIds.forEach(userRepository::deleteById);
		});
		createdUserIds.clear();
		createdFriendIds.clear();
		conversationId = null;
	}

	/**
	 * Built from JSON with the app's own mapper rather than a constructor:
	 * SendDirectMessageRequest has neither an all-args constructor nor setters,
	 * and going through the mapper is the same path the controller takes.
	 */
	private MessageResponse send(Long existingConversationId, String content) {
		String body = existingConversationId == null
				? "{\"recipientId\":\"%s\",\"content\":\"%s\"}".formatted(readerId, content)
				: "{\"recipientId\":\"%s\",\"conversationId\":%d,\"content\":\"%s\"}"
						.formatted(readerId, existingConversationId, content);
		return messageService.sendDirectMessage(
				json.readValue(body, SendDirectMessageRequest.class), senderId);
	}

	@Test
	void theBroadcastCarriesTheCounterAsItIsAfterTheIncrement(@Autowired EventCapture captured) {
		captured.events.clear();

		conversationId = send(null, "xin chào").getConversationId();

		// The row says 1 ...
		assertThat(participantRepo.findByConversationIdAndUserId(conversationId, readerId)
				.orElseThrow().getUnreadCounts()).isEqualTo(1L);

		// ... and so must the payload. It used to say 0, because the DTO was
		// mapped before the counter SQL ran -- for every message, for ever.
		ConversationResponse payload = captured.events.getLast().getConversation();
		assertThat(unreadFor(payload, readerId)).isEqualTo(1L);
		assertThat(unreadFor(payload, senderId)).isZero();
	}

	@Test
	void aSecondMessageAfterAReadBringsTheCounterBack() {
		conversationId = send(null, "tin thứ nhất").getConversationId();

		conversationService.markAsSeen(conversationId, readerId);
		assertThat(participantRepo.findByConversationIdAndUserId(conversationId, readerId)
				.orElseThrow().getUnreadCounts()).isZero();

		send(conversationId, "tin thứ hai");

		transactions.executeWithoutResult(status -> {
			Participant readerRow = participantRepo.findByConversationIdAndUserId(conversationId, readerId)
					.orElseThrow();
			assertThat(readerRow.getUnreadCounts()).isEqualTo(1L);
			// Still pointing at message 1: the read receipt must not drift forward
			// onto a message the reader never saw.
			assertThat(readerRow.getLastSeenMessage().getContent()).isEqualTo("tin thứ nhất");
		});
	}

	@Test
	void markSeenWritesWithoutDependingOnAnythingItRead() {
		conversationId = send(null, "tin thứ nhất").getConversationId();

		// The shape of the old bug, made deterministic: hold a participant loaded
		// at unread = 1, let a sender's increment land underneath it, and only
		// then mark as seen. The old code flushed the stale copy and lost the
		// increment; markSeen reads nothing, so the final state is exactly what
		// it asked for.
		transactions.executeWithoutResult(status -> {
			Participant stale = participantRepo.findByConversationIdAndUserId(conversationId, readerId)
					.orElseThrow();
			assertThat(stale.getUnreadCounts()).isEqualTo(1L);

			participantRepo.incrementUnreadCountsForOthers(conversationId, senderId);

			participantRepo.markSeen(conversationId, readerId,
					conversationRepo.findByIdWithGraph(conversationId).orElseThrow().getLastMessage(),
					Instant.now());
		});

		assertThat(participantRepo.findByConversationIdAndUserId(conversationId, readerId)
				.orElseThrow().getUnreadCounts()).isZero();
	}

	@Test
	void theSenderOfTheNewestMessageStillGetsTheirOwnRowWritten() {
		conversationId = send(null, "của tôi").getConversationId();

		conversationService.markAsSeen(conversationId, senderId);

		// The early return used to sit before the write, so the sender's own
		// lastReadAt and lastSeenMessage stayed stuck wherever they were.
		transactions.executeWithoutResult(status -> {
			Participant senderRow = participantRepo.findByConversationIdAndUserId(conversationId, senderId)
					.orElseThrow();
			assertThat(senderRow.getLastReadAt()).isNotNull();
			assertThat(senderRow.getLastSeenMessage()).isNotNull();
		});
	}

	@Test
	void aNonMemberIsRefusedRatherThanToldWhatExists() {
		conversationId = send(null, "riêng tư").getConversationId();

		String outsiderId = transactions.execute(status -> {
			User outsider = userRepository.save(User.builder()
					.email("unread-outsider-" + System.nanoTime() + "@example.com")
					.displayName("Outsider").emailVerified(true).build());
			createdUserIds.add(outsider.getId());
			return outsider.getId();
		});

		// It used to answer PARTICIPANT_NOT_FOUND here and 200 for an empty
		// conversation, which told an outsider which ids exist and which carry
		// traffic. Now it is the refusal getMessages already gave.
		assertThatThrownBy(() -> conversationService.markAsSeen(conversationId, outsiderId))
				.hasMessageContaining("not a member");
	}

	private long unreadFor(ConversationResponse conversation, String userId) {
		return conversation.getParticipants().stream()
				.filter(p -> p.getUserId().equals(userId))
				.findFirst().orElseThrow()
				.getUnreadCounts();
	}
}
