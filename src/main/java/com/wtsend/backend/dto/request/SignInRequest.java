package com.wtsend.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
/**
 * {@code @AllArgsConstructor} is what makes this deserialisable, and it is not
 * decoration: Lombok's {@code @Builder} generates an all-args constructor at
 * package level and suppresses the default one, so under Jackson 3 this class
 * had no creator at all and every request body carrying it answered 500.
 * Jackson matches the public constructor's parameters by name, which works
 * because Spring Boot compiles with {@code -parameters}.
 */
@AllArgsConstructor
@Builder
public class SignInRequest {

	@NotBlank(message = "Email is required")
	@Email(message = "Invalid email format")
	private String email;

	@NotBlank(message = "Password is required")
	private String password;
}
