package com.wtsend.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleAuthRequest {
	@NotBlank(message = "Credential is required")
	private String credential;
}