package com.wtsend.backend.messaging.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import com.wtsend.backend.messaging.MessagingProperties;

import lombok.RequiredArgsConstructor;

/**
 * Declares the topics so a local stack comes up ready to use with
 * {@code auto.create.topics.enable=false} on the broker -- which stays off in
 * every environment, because with it on a typo in a topic name silently
 * creates a one-partition topic and the producer writes into nowhere.
 *
 * <p>
 * In production these beans are expected to find the topics already there.
 * {@code KafkaAdmin} only ever creates a missing topic, never adds partitions
 * to an existing one, so the numbers here are a description of what should
 * exist and not a guarantee of what does -- and the application's own
 * principal should not hold CreateTopics anyway.
 */
@Configuration
@RequiredArgsConstructor
public class KafkaTopicsConfig {

	private final MessagingProperties properties;

	@Bean
	NewTopic emailNotificationsTopic() {
		MessagingProperties.Topic topic = properties.getEmailNotifications();
		return TopicBuilder.name(topic.getName())
				.partitions(topic.getPartitions())
				.replicas(topic.getReplicas())
				.build();
	}

	/**
	 * Dead letters are kept far longer than the live topic: their whole purpose
	 * is to still be there when somebody comes looking for why a user never
	 * received an email.
	 */
	@Bean
	NewTopic emailNotificationsDltTopic() {
		MessagingProperties.Topic topic = properties.getEmailNotifications();
		return TopicBuilder.name(topic.dltName())
				.partitions(topic.getPartitions())
				.replicas(topic.getReplicas())
				.config("retention.ms", String.valueOf(30L * 24 * 60 * 60 * 1000))
				.build();
	}
}
