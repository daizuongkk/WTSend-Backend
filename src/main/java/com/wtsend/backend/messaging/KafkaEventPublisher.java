package com.wtsend.backend.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.wtsend.backend.messaging.event.EmailRequestedEvent;
import com.wtsend.backend.messaging.event.EmailRequestedEvent.EmailKind;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * The one place anything is written to Kafka.
 *
 * <p>
 * Everything goes through here on purpose. Adding a topic later is an edit to
 * this class rather than a search for every producer, and the day the outbox
 * pattern is wanted, {@link #publishEmail} becomes a row insert and nothing
 * above it changes.
 *
 * <h2>What is given up by not having an outbox</h2>
 *
 * Kafka transactions cannot be atomic with MySQL -- {@code transactional.id}
 * makes writes atomic among themselves on the Kafka side only, and the two are
 * independent resource managers with no XA between them.
 * {@code ChainedKafkaTransactionManager} is best-effort 1PC, which fails in
 * exactly the way it is meant to prevent, so it is not used.
 *
 * <p>
 * The real failure mode: the database commits, the send fails, and the user
 * exists with no email sent and no record of the attempt. That drops the
 * guarantee from "every state change produces exactly one event" to "at most
 * one event". What is lost is the event, never the data. It is survivable here
 * for one specific reason: {@code /api/send-verify-email} already exists, so a
 * user who never got their mail can ask for another.
 *
 * <p>
 * What makes it survivable rather than invisible is this class failing loudly:
 * {@code send} returns a future, and a future nobody looks at is a silent
 * loss, so every one of them is logged and counted.
 */
@Component
@Slf4j
public class KafkaEventPublisher {

	static final String FAILURE_COUNTER = "wtsend.kafka.send.failures";

	private final KafkaTemplate<Object, Object> kafka;
	private final MessagingProperties topics;
	private final MeterRegistry meters;

	public KafkaEventPublisher(KafkaTemplate<Object, Object> kafka, MessagingProperties topics,
			MeterRegistry meters) {
		this.kafka = kafka;
		this.topics = topics;
		this.meters = meters;
	}

	/**
	 * Asks for one of our emails to be sent, out of band.
	 *
	 * <p>
	 * Keyed by user id, so everything addressed to one person stays in one
	 * partition and therefore in order -- two verification requests cannot
	 * overtake each other.
	 */
	public void publishEmail(EmailKind kind, String userId, String traceId) {
		EmailRequestedEvent event = EmailRequestedEvent.of(kind, userId, traceId);
		String topic = topics.getEmailNotifications().getName();

		try {
			kafka.send(topic, userId, event)
					.whenComplete((result, failure) -> {
						if (failure != null)
							recordFailure(topic, event, failure);
					});
		} catch (Exception e) {
			// send itself throws when it cannot even buffer -- most often
			// max.block.ms expiring because the broker is unreachable and no
			// metadata is available.
			recordFailure(topic, event, e);
		}
	}

	private void recordFailure(String topic, EmailRequestedEvent event, Throwable failure) {
		Counter.builder(FAILURE_COUNTER)
				.description("Events that could not be written to Kafka at all")
				.tag("topic", topic)
				.register(meters)
				.increment();
		// ERROR, with the ids needed to work out which user never got their
		// email. Nothing retries this: the event is gone.
		log.error("Failed to publish {} eventId={} userId={} to {}",
				event.kind(), event.eventId(), event.userId(), topic, failure);
	}
}
