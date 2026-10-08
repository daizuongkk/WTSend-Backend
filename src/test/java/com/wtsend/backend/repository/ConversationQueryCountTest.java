package com.wtsend.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.wtsend.backend.libs.ConversationMapper;
import com.wtsend.backend.model.Conversation;
import com.wtsend.backend.model.GroupInfo;
import com.wtsend.backend.model.Participant;
import com.wtsend.backend.model.User;
import com.wtsend.backend.model.enums.ConversationType;

import jakarta.persistence.EntityManager;

/**
 * Pins the query count for the conversation list.
 *
 * <p>
 * Listing conversations used to be an N+1: one query for the list, then per
 * conversation one for its participants, one per participant for the user,
 * plus lastMessage, its sender, the group and its creator. It was invisible
 * because open-in-view kept a session open during response rendering, so the
 * extra round trips just made the endpoint slow rather than failing.
 *
 * <p>
 * The assertion is that the count does not grow with the number of
 * conversations -- not a specific number, which would break on any harmless
 * change to the fetch graph.
 */
@SpringBootTest
@Transactional
class ConversationQueryCountTest {

	@DynamicPropertySource
	static void enableStatistics(DynamicPropertyRegistry registry) {
		// Only for this test; the application config stays untouched.
		registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
		// That property alone forks a second application context, and
		// SocketIOServerRunner would then try to bind 9092 while the context
		// cached for BackendApplicationTests still holds it. Ephemeral port
		// instead -- nothing here talks to the socket server.
		registry.add("socketio.port", () -> "0");
	}

	@Autowired
	private EntityManager entityManager;
	@Autowired
	private ConversationRepository conversationRepo;
	@Autowired
	private ConversationMapper conversationMapper;

	private Statistics statistics;

	@BeforeEach
	void resetStatistics() {
		statistics = entityManager.getEntityManagerFactory()
				.unwrap(SessionFactory.class)
				.getStatistics();
		statistics.clear();
	}

	private User persistUser(String email) {
		User user = User.builder()
				.email(email)
				.displayName(email.substring(0, email.indexOf('@')))
				.build();
		entityManager.persist(user);
		return user;
	}

	private void persistGroupConversation(User owner, User other, String name) {
		Conversation conversation = new Conversation();
		conversation.setType(ConversationType.GROUP);

		List<Participant> participants = new ArrayList<>();
		for (User member : List.of(owner, other)) {
			Participant participant = new Participant();
			participant.setUser(member);
			participant.setConversation(conversation);
			participant.setUnreadCounts(0L);
			participants.add(participant);
		}
		conversation.setParticipants(participants);
		conversation.setGroup(GroupInfo.builder()
				.conversation(conversation)
				.name(name)
				.creator(owner)
				.build());

		entityManager.persist(conversation);
	}

	/** Loads the list and maps it exactly as the service does, then counts. */
	private long queriesToListAndMap(String userId) {
		entityManager.flush();
		entityManager.clear();
		statistics.clear();

		conversationRepo.findConversations(userId)
				.forEach(conversationMapper::toResponse);

		// Prepared statements, not query executions: this counts the actual SQL
		// round trips, including the ones a lazy association triggers behind the
		// mapper rather than through a named query.
		return statistics.getPrepareStatementCount();
	}

	@Test
	void listingConversationsDoesNotScaleQueriesWithConversationCount() {
		User alice = persistUser("alice-qc@example.com");

		persistGroupConversation(alice, persistUser("bob-qc@example.com"), "one");
		long withOne = queriesToListAndMap(alice.getId());

		for (int i = 0; i < 4; i++) {
			persistGroupConversation(alice, persistUser("member-qc-" + i + "@example.com"), "extra-" + i);
		}
		long withFive = queriesToListAndMap(alice.getId());

		assertThat(withFive)
				.as("5 conversations must cost the same as 1 -- %d vs %d means an N+1 came back",
						withFive, withOne)
				.isEqualTo(withOne);
	}

	@Test
	void theWholeMappedGraphCostsASingleQuery() {
		User alice = persistUser("alice-single@example.com");
		persistGroupConversation(alice, persistUser("bob-single@example.com"), "only");

		assertThat(queriesToListAndMap(alice.getId())).isEqualTo(1);
	}
}
