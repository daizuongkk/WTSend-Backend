package com.wtsend.backend.dtos.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthResponse {

	@Builder.Default
	Boolean success = false;
	String message;
	String username;
	String email;
	String displayName;
	String accessToken;
	String refreshToken;

}
