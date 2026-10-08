package com.wtsend.backend.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * Every instance runs its own scheduler, so a {@code @Scheduled} job fires once
 * per instance. {@code @SchedulerLock} narrows that to one real execution.
 *
 * <p>
 * Backed by the Redis already running rather than a database table: there is
 * one job and the lock is advisory. If Redis loses it, two instances can run
 * the same sweep -- harmless, because the cleanup is a single conditional
 * DELETE and the loser removes nothing.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

	@Bean
	public LockProvider lockProvider(RedisConnectionFactory redisConnectionFactory) {
		return new RedisLockProvider(redisConnectionFactory, "wtsend");
	}
}
