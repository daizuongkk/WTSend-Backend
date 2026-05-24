package com.wtsend.backend.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

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
