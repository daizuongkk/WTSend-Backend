package com.wtsend.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequest {

	@NotBlank(message = "Reset token is required")
	private String token;

	@NotBlank(message = "New password is required")
	@Size(min = 8, max = 64, message = "Password must be from 8 to 64 characters.")
	// Same policy as SignUpRequest and ChangePasswordRequest -- a reset must not
	// be a way around it.
	@Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).*$", message = "Password must contain at least one digit, one lowercase, one uppercase, and one special character")
	private String newPassword;
}
