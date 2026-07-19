package com.wtsend.backend.dto.request;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserRequest {
	@NotBlank(message = "Username is required")
	@Size(min = 8, max = 20, message = "Username must be from 8 to 20 characters.")
	private String username;

	@NotBlank(message = "Email is required")
	@Email(message = "Invalid email format")
	private String email;

	private MultipartFile avatar;

	@NotEmpty(message = "Display name is required")
	private String displayName;
	@Pattern(regexp = "^(0[1-9]|\\d{2}|3[01])/(0[1-9]|1[012])/(19|20)\\d{2}$", message = "Invalid date format")
	private String birthday;
	@Pattern(regexp = "^$|^[0-9]{10,15}$", message = "Số điện thoại không hợp lệ")
	private String phone;

	@Size(max = 200, message = "The maximum bio length is 200 characters.")
	private String bio;
}
