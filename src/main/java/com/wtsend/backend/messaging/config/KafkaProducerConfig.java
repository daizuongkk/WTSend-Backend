package com.wtsend.backend.messaging.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

/**
 * The one template everything is written with, and the reason it is built by
 * hand.
 *
 * <p>
 * Events are JSON, but dead letters are not always: a record that failed to
 * deserialise reaches the recoverer as the raw {@code byte[]} that could not be
 * parsed, and a JSON serializer turns a byte array into a base64 string. The
 * dead-letter topic then held {@code "e3t7"} where the original bytes were
 * <code>{{{</code>, so replaying a poison pill replayed base64 rather than the
 * message -- the promise that the DLT keeps the original bytes was simply not
 * true. One serializer dispatching on type covers both.
 *
 * <p>
 * This replaces the template Boot would autoconfigure, which is why the
 * producer's serializers are not set in properties: declaring a
 * {@code KafkaTemplate} bean switches Boot's own off
 * ({@code @ConditionalOnMissingBean}), so a {@code value-serializer} property
 * would be configuration that silently applies to nothing. The connection and
 * delivery settings still come from {@code spring.kafka.producer.*}, by way of
 * the autoconfigured {@link ProducerFactory} whose configuration is copied
 * here.
 */
@Configuration
public class KafkaProducerConfig {

	@Bean
	KafkaTemplate<Object, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
		Map<Class<?>, Serializer<?>> byType = new LinkedHashMap<>();
		byType.put(byte[].class, new ByteArraySerializer());
		byType.put(String.class, new StringSerializer());
		// noTypeInfo() rather than the spring.json.add.type.headers property,
		// which this serializer never sees. No Java class name goes on the wire:
		// the default writes __TypeId__ with the FQCN, tying the wire format to a
		// package name, so renaming the package breaks every record in flight and
		// renaming the class breaks the consumers still running the old build.
		byType.put(Object.class, new JacksonJsonSerializer<>().noTypeInfo());

		// assignable = true, so a subclass of anything mapped above still finds
		// its serializer instead of falling off the end. Same serializer for the
		// key: if it was the key that failed to deserialise, it arrives as
		// byte[] too.
		DelegatingByTypeSerializer serializer = new DelegatingByTypeSerializer(byType, true);

		return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
				producerFactory.getConfigurationProperties(), serializer, serializer));
	}
}
