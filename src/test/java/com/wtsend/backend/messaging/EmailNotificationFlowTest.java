package com.wtsend.backend.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.messaging.config.KafkaErrorHandlingConfig;
import com.wtsend.backend.messaging.config.KafkaProducerConfig;
import com.wtsend.backend.messaging.config.KafkaTopicsConfig;
import com.wtsend.backend.messaging.consumer.EmailNotificationConsumer;
import com.wtsend.backend.messaging.event.EmailRequestedEvent.EmailKind;
import com.wtsend.backend.model.User;
import com.wtsend.backend.repository.UserRepository;
import com.wtsend.backend.services.EmailService;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Producer and consumer end to end, over a real broker, with nothing else
 * loaded.
 *
 * <p>
 * A slice rather than {@code @SpringBootTest}: the full context needs MySQL and
 * Redis, and none of what is being checked here involves either. The broker is
 * embedded rather than a Testcontainer because every behaviour worth pinning
 * down is client-side, and {@code @EmbeddedKafka} exercises the real
 * kafka-clients code path to reach it.
 */
@SpringJUnitConfig
// Only the Kafka autoconfiguration, imported by hand. @EnableAutoConfiguration
// would drag in the datasource, Flyway and JPA, which is the opposite of the
// point -- the context then fails on "Failed to determine a suitable driver
// class" before any of this is reached.
@EnableConfigurationProperties(MessagingProperties.class)
@Import({ KafkaAutoConfiguration.class, KafkaTopicsConfig.class, KafkaProducerConfig.class,
		KafkaErrorHandlingConfig.class,
		// MessagingProperties is registered by @EnableConfigurationProperties above;
		// importing it here as well gives two beans of that type and nothing binds.
		KafkaEventPublisher.class, EmailNotificationConsumer.class,
		EmailNotificationFlowTest.TestBeans.class })
@EmbeddedKafka(partitions = 1, topics = { EmailNotificationFlowTest.TOPIC, EmailNotificationFlowTest.DLT },
		bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = {
		"app.messaging.email-notifications.name=" + EmailNotificationFlowTest.TOPIC,
		"spring.kafka.consumer.group-id=test-email-notifications",
		"spring.kafka.consumer.auto-offset-reset=earliest",
		"spring.kafka.consumer.enable-auto-commit=false",
		"spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
		"spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
		"spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JacksonJsonDeserializer",
		"spring.kafka.consumer.properties.spring.json.value.default.type=com.wtsend.backend.messaging.event.EmailRequestedEvent",
		"spring.kafka.consumer.properties.spring.json.use.type.headers=false",
		// No producer serializers here: KafkaProducerConfig owns them.
		"spring.kafka.producer.acks=all",
		// The same timing values the application runs with, on purpose. An
		// invalid combination (delivery.timeout.ms < linger.ms +
		// request.timeout.ms) stops the producer being constructed at all, and
		// that failed only in the real stack while this test passed.
		"spring.kafka.producer.properties.enable.idempotence=true",
		"spring.kafka.producer.properties.linger.ms=5",
		"spring.kafka.producer.properties.request.timeout.ms=10000",
		"spring.kafka.producer.properties.delivery.timeout.ms=30000",
		"spring.kafka.producer.properties.max.block.ms=5000",
		"spring.kafka.listener.ack-mode=record",
		"spring.kafka.listener.missing-topics-fatal=false",
		"spring.kafka.admin.fail-fast=false",
})
class EmailNotificationFlowTest {

	static final String TOPIC = "test.notify.email.v1";
	static final String DLT = TOPIC + ".dlt";

	@EnableKafka
	@Configuration
	static class TestBeans {
		@Bean
		MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}
	}

	@MockitoBean
	private UserRepository userRepository;
	@MockitoBean
	private EmailService emailService;

	@Autowired
	private KafkaEventPublisher publisher;
	@Autowired
	private EmbeddedKafkaBroker broker;

	/**
	 * Reads the dead-letter topic from the beginning and returns what is there,
	 * keyed by record key.
	 *
	 * <p>
	 * A fresh group each time, and every assertion filters by the key its own
	 * test used. The tests share one broker and JUnit does not promise an order,
	 * so a single shared consumer had them reading each other's dead letters --
	 * and one "there should be no dead letter" assertion is only meaningful
	 * about its own record.
	 */
	private Map<String, String> deadLetters(Duration patience) {
		Map<String, Object> config = KafkaTestUtils.consumerProps(
				broker.getBrokersAsString(), "dlt-reader-" + UUID.randomUUID(), "false");
		config.put("auto.offset.reset", "earliest");
		Map<String, String> found = new LinkedHashMap<>();
		try (Consumer<String, String> consumer = new KafkaConsumer<>(config,
				new StringDeserializer(), new StringDeserializer())) {
			consumer.subscribe(List.of(DLT));
			long deadline = System.currentTimeMillis() + patience.toMillis();
			do {
				for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500)))
					found.put(record.key(), record.value());
			} while (System.currentTimeMillis() < deadline);
		}
		return found;
	}

	@Test
	void aPublishedEventReachesTheConsumerAndSendsTheEmail() {
		User user = User.builder().email("someone@example.com").displayName("Someone").build();
		when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

		publisher.publishEmail(EmailKind.VERIFY_LINK, "user-1", "trace-1");

		await().atMost(Duration.ofSeconds(20))
				.untilAsserted(() -> verify(emailService).sendVerifyLink(user));
	}

	@Test
	void aCooldownCountsAsDoneRatherThanAFailure() {
		User user = User.builder().email("cooldown@example.com").displayName("Cooldown").build();
		when(userRepository.findById("user-2")).thenReturn(Optional.of(user));
		// Within 30s of the previous send EmailService refuses. That is the
		// deduplication working, not an error: the record must be acknowledged
		// and must not reach the DLT.
		doThrow(new AppException(ErrorCode.VERIFY_EMAIL_COOLDOWN))
				.when(emailService).sendVerifyLink(user);

		publisher.publishEmail(EmailKind.VERIFY_LINK, "user-2", "trace-2");

		await().atMost(Duration.ofSeconds(20))
				.untilAsserted(() -> verify(emailService).sendVerifyLink(user));

		assertThat(deadLetters(Duration.ofSeconds(3)))
				.as("a cooldown must not become a dead letter")
				.doesNotContainKey("user-2");
	}

	@Test
	void aPasswordResetCooldownIsAlsoDoneRatherThanAFailure() {
		User user = User.builder().email("reset-cooldown@example.com").displayName("Reset").build();
		when(userRepository.findById("user-5")).thenReturn(Optional.of(user));
		doThrow(new AppException(ErrorCode.PASSWORD_RESET_COOLDOWN))
				.when(emailService).sendPasswordResetLink(user);

		publisher.publishEmail(EmailKind.PASSWORD_RESET, "user-5", "trace-5");

		await().atMost(Duration.ofSeconds(20))
				.untilAsserted(() -> verify(emailService).sendPasswordResetLink(user));

		// This one used to burn the retries and land in the DLT, where a replay
		// would send a second reset link and retire the live one the user was
		// probably about to click.
		assertThat(deadLetters(Duration.ofSeconds(3)))
				.as("a password reset cooldown must not become a dead letter")
				.doesNotContainKey("user-5");
	}

	@Test
	void aPoisonPillGoesToTheDltAndDoesNotStallThePartition() {
		// Bytes that are not valid JSON at all. Without ErrorHandlingDeserializer
		// this throws inside poll(), the offset never advances, and this
		// partition spins on the same record for ever -- so the real assertion is
		// the one after it: a valid record still gets through.
		try (KafkaProducer<String, String> raw = new KafkaProducer<>(
				KafkaTestUtils.producerProps(broker.getBrokersAsString()),
				new StringSerializer(), new StringSerializer())) {
			raw.send(new ProducerRecord<>(TOPIC, "user-3", "{{{"));
			raw.flush();
		}

		// The original bytes, verbatim. They used to arrive base64-encoded,
		// because the recoverer's template serialised the byte[] as JSON.
		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(
				deadLetters(Duration.ofSeconds(2)))
				.as("the malformed record should be in the DLT, unchanged")
				.containsEntry("user-3", "{{{"));

		User user = User.builder().email("after@example.com").displayName("After").build();
		when(userRepository.findById("user-4")).thenReturn(Optional.of(user));
		publisher.publishEmail(EmailKind.VERIFY_LINK, "user-4", "trace-4");

		await().atMost(Duration.ofSeconds(20))
				.untilAsserted(() -> verify(emailService).sendVerifyLink(user));
	}

	@Test
	void aDeterministicFailureGoesToTheDltWithoutFiveAttempts() {
		when(userRepository.findById("missing")).thenReturn(Optional.empty());

		publisher.publishEmail(EmailKind.VERIFY_LINK, "missing", "trace-5");

		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(
				deadLetters(Duration.ofSeconds(2)))
				.containsKey("missing"));
		// No email attempt was ever possible, so nothing should have been asked
		// of EmailService.
		verify(emailService, org.mockito.Mockito.never()).sendVerifyLink(any(User.class));
	}
}
