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

/**
 * Mirrors {@link EmailVerificationToken}: Redis with a native TTL, so an unused
 * link expires without anything having to sweep it.
 *
 * <p>
 * Longer-lived than a verification token (30 minutes against 15) because a
 * reset is requested and then acted on later -- people go looking for the mail
 * rather than having it arrive mid-signup.
 */
@RedisHash("password_reset_token")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetToken {

	public static final long TTL_MINUTES = 30L;

	/** How long before another reset mail may be requested for the same user. */
	public static final long COOLDOWN_SECONDS = 30L;

	@Id
	private String token;

	@Indexed
	private String userId;

	@TimeToLive(unit = TimeUnit.MINUTES)
	private Long expiresIn;

	private Instant cooldownUntil;
}
