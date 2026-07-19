package com.wtsend.backend.model;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;
import org.springframework.data.redis.core.index.Indexed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@RedisHash("email_verification_token")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationToken {
	@Id
	private String token;

	@Indexed
	private String userId;

	@TimeToLive(unit = TimeUnit.MINUTES)
	private Long expiresIn;

	private Instant cooldownUntil;
}
