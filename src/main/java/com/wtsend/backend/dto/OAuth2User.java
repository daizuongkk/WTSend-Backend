package com.wtsend.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OAuth2User {
	private String email;
	private String name;
	private String providerId;
}
