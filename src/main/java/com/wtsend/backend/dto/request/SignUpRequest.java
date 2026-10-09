package com.wtsend.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

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
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SignUpRequest {
	// @NotBlank(message = "Username is required")
	// @Size(min = 8, max = 32, message = "Username must be from 8 to 32
	// characters.")
	// String username;
	@NotBlank(message = "Email is required")
	@Email(message = "Invalid email format")
	String email;
	@NotBlank(message = "Password cannot be empty")
	@Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
	@Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).*$", message = "Password must contain at least one digit, one lowercase, one uppercase, and one special character")
	private String password;

	// @NotEmpty(message = "FirstName is required")
	// String firstName;
	// @NotEmpty(message = "LastName is required")
	// String lastName;

}
