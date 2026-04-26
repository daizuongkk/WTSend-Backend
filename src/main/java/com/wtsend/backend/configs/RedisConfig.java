// package com.wtsend.backend.configs;

// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import
// org.springframework.data.redis.connection.RedisStandaloneConfiguration;
// import
// org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
// import org.springframework.data.redis.core.RedisTemplate;
// import
// org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
// import
// org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
// import org.springframework.data.redis.serializer.StringRedisSerializer;

// @Configuration
// @EnableRedisRepositories
// public class RedisConfig {

// @Bean
// public LettuceConnectionFactory redisConnectionFactory() {
// return new LettuceConnectionFactory(
// new RedisStandaloneConfiguration("localhost", 6379));
// }

// @Bean
// public RedisTemplate<String, Object> redisTemplate(
// LettuceConnectionFactory connectionFactory) {

// RedisTemplate<String, Object> template = new RedisTemplate<>();
// template.setConnectionFactory(connectionFactory);

// template.setKeySerializer(new StringRedisSerializer());
// template.setHashKeySerializer(new StringRedisSerializer());
// template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
// template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

// template.afterPropertiesSet();
// return template;
// }
// }
