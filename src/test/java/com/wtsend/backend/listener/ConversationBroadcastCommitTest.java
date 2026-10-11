package com.wtsend.backend.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.corundumstudio.socketio.BroadcastOperations;
import com.corundumstudio.socketio.SocketIOServer;
import com.wtsend.backend.dto.response.ConversationResponse;
import com.wtsend.backend.dto.response.ParticipantResponse;
import com.wtsend.backend.model.Conversation;
import com.wtsend.backend.model.Message;
import com.wtsend.backend.model.Participant;
import com.wtsend.backend.model.User;
import com.wtsend.backend.model.enums.ConversationType;
import com.wtsend.backend.repository.ConversationRepository;
import com.wtsend.backend.repository.MessageRepository;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.interfaces.IConversationService;
import com.wtsend.backend.socket.SocketRooms;

/**
 * markAsSeen used to emit read-message from inside its own open transaction, so
 * a client could be told a message had been read before the write was durable
 * -- or at all, had anything later rolled the transaction back.
 *
 * <p>
 * The guarantee pinned here is the one Kafka will inherit when the broadcast
 * becomes a produce: nothing leaves the process until the database has
 * committed.
 */
@SpringBootTest
class ConversationBroadcastCommitTest {

	/** Also keeps the real server from binding port 9092 a second time. */
	@MockitoBean
	private SocketIOServer server;

	@Autowired
	private IConversationService conversationService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ConversationRepository conversationRepo;
	@Autowired
	private MessageRepository messageRepo;
	@Autowired
	private PlatformTransactionManager transactionManager;

	/** Not a bean, just the handle the server returns. */
	private final BroadcastOperations roomOperations = Mockito.mock(BroadcastOperations.class);

	private TransactionTemplate transactions;
	private Long conversationId;
	private String readerId;
	private Long lastMessageId;
	private final List<String> createdUserIds = new ArrayList<>();

	@BeforeEach
	void setUp() {
		transactions = new TransactionTemplate(transactionManager);
		when(server.getRoomOperations(any())).thenReturn(roomOperations);

		transactions.executeWithoutResult(status -> {
			User sender = userRepository.save(User.builder()
					.email("sender-" + System.nanoTime() + "@example.com").displayName("Sender").build());
			User reader = userRepository.save(User.builder()
					.email("reader-" + System.nanoTime() + "@example.com").displayName("Reader").build());
			readerId = reader.getId();
			createdUserIds.add(sender.getId());
			createdUserIds.add(reader.getId());

			Conversation conversation = new Conversation();
			conversation.setType(ConversationType.DIRECT);
			conversation.setDirectKey(Conversation.directKeyOf(sender.getId(), reader.getId()));
			List<Participant> participants = new ArrayList<>();
			for (User member : List.of(sender, reader)) {
				Participant participant = new Participant();
				participant.setUser(member);
				participant.setConversation(conversation);
				participant.setUnreadCounts(1L);
				participants.add(participant);
			}
			conversation.setParticipants(participants);
			conversationRepo.save(conversation);

			Message message = messageRepo.save(Message.builder()
					.sender(sender).conversation(conversation).content("unread").build());
			conversation.setLastMessage(message);
			conversationRepo.save(conversation);

			conversationId = conversation.getId();
			lastMessageId = message.getId();
		});
	}

	/**
	 * This test cannot use @Transactional -- it has to commit for AFTER_COMMIT to
	 * fire at all -- so it has to clean up after itself. Left behind, its users
	 * sat in the dev database until they were a day old, at which point
	 * UnverifiedAccountCleanupJobTest tried to delete them and hit the foreign key
	 * from their messages.
	 */
	@AfterEach
	void removeWhatThisTestCommitted() {
		transactions.executeWithoutResult(status -> {
			conversationRepo.findByIdWithGraph(conversationId).ifPresent(conversation -> {
				// conversations.last_message_id and participants.last_seen_message_id
				// both point at rows the cascade is about to remove, so clear them
				// and flush first -- otherwise whether this passes depends on the
				// order Hibernate happens to delete the children in.
				conversation.setLastMessage(null);
				conversation.getParticipants().forEach(participant -> participant.setLastSeenMessage(null));
				conversationRepo.saveAndFlush(conversation);
				// cascade = ALL on participants and messages takes both with it.
				conversationRepo.delete(conversation);
			});
			conversationRepo.flush();
			createdUserIds.forEach(userRepository::deleteById);
		});
		createdUserIds.clear();
	}

	@Test
	void aCommittedReadReceiptIsBroadcast() {
		conversationService.markAsSeen(conversationId, readerId);

		// Addressed to the participants' own rooms rather than the
		// conversation's -- see SocketRelay.toConversation for why.
		createdUserIds.forEach(userId -> verify(server).getRoomOperations(eq(SocketRooms.user(userId))));
		verify(roomOperations, times(createdUserIds.size()))
				.sendEvent(eq("read-message"), any(Object.class));
	}

	@Test
	void theBroadcastReceiptCarriesTheStateAfterTheRead() {
		conversationService.markAsSeen(conversationId, readerId);

		ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		verify(roomOperations, atLeastOnce()).sendEvent(eq("read-message"), payload.capture());

		ConversationResponse broadcast = (ConversationResponse) ((Map<?, ?>) payload.getValue())
				.get("conversation");
		ParticipantResponse reader = broadcast.getParticipants().stream()
				.filter(p -> p.getUserId().equals(readerId))
				.findFirst().orElseThrow();

		// markSeen clears the persistence context, so the entity this used to be
		// mapped from was detached and still holding its pre-update values. The
		// payload said unreadCounts=1 and pointed lastSeenMessageId at the message
		// before this one -- so the reader's own badge re-lit from the echo, and
		// the sender's "seen" marker never reached the newest message.
		assertThat(reader.getUnreadCounts()).isZero();
		assertThat(reader.getLastSeenMessageId()).isEqualTo(lastMessageId);
	}

	@Test
	void aRolledBackReadReceiptIsNotBroadcast() {
		// markAsSeen joins this transaction, so rolling it back means the write
		// never lands -- and the AFTER_COMMIT listener must not run either.
		transactions.executeWithoutResult(status -> {
			conversationService.markAsSeen(conversationId, readerId);
			status.setRollbackOnly();
		});

		verify(roomOperations, never()).sendEvent(eq("read-message"), any(Object.class));

		// And the write really did roll back.
		assertThat(conversationRepo.findByIdWithGraph(conversationId).orElseThrow()
				.getParticipants().stream()
				.filter(p -> p.getUser().getId().equals(readerId))
				.findFirst().orElseThrow()
				.getUnreadCounts())
				.isEqualTo(1L);
	}
}
