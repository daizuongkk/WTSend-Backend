package com.wtsend.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RefreshTokenRequest {
	@NotBlank(message = "token is required")
	private String token;
}
