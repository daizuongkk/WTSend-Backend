package com.wtsend.backend.messaging.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

import com.wtsend.backend.messaging.consumer.NonRetryableEventException;

import tools.jackson.core.JacksonException;

/**
 * What happens to a record the listener could not handle.
 *
 * <p>
 * Five attempts with a growing gap, then the record is written to
 * {@code <topic>.dlt} with the original bytes in a header and the offset moves
 * on. A single bad record must not hold up the partition behind it.
 *
 * <p>
 * No Kafka health indicator is registered anywhere, deliberately. This
 * application is built to keep serving every request while Kafka is down;
 * wiring that into {@code /actuator/health} would let a broker outage fail a
 * liveness probe and have the orchestrator kill otherwise healthy pods.
 */
@Configuration
public class KafkaErrorHandlingConfig {

	/**
	 * Boot applies a single {@link CommonErrorHandler} bean to the listener
	 * container factory it auto-configures, so this is all the wiring needed.
	 */
	@Bean
	CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
		// ExponentialBackOffWithMaxRetries is gone in Spring 7; ExponentialBackOff
		// now carries maxAttempts itself. Attempts, not retries: 5 means the
		// first try plus four more.
		ExponentialBackOff backOff = new ExponentialBackOff();
		backOff.setInitialInterval(1_000L);
		backOff.setMultiplier(2.0);
		backOff.setMaxInterval(10_000L);
		backOff.setMaxAttempts(5);

		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
				// -1 leaves the partition to the producer, which picks it from the
				// key -- the DLT need not have as many partitions as the source.
				(record, exception) -> new TopicPartition(record.topic() + ".dlt", -1));

		DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

		// Retrying these would only burn the backoff and arrive at the same
		// answer. Note what is NOT here: AppException as a whole. It covers both
		// the deterministic (USER_NOT_FOUND) and the genuinely transient
		// (EMAIL_SEND_FAILED wrapping a 503 from Resend, which must be retried),
		// so the consumer sorts it by ErrorCode and throws
		// NonRetryableEventException for the deterministic ones instead.
		handler.addNotRetryableExceptions(
				NonRetryableEventException.class,
				DeserializationException.class,
				JacksonException.class,
				IllegalArgumentException.class);

		return handler;
	}
}
