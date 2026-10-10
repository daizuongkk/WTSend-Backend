package com.wtsend.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
	String email;
	boolean emailVerified;
	String accessToken;
	/**
	 * Reaches the controller so it can build the httpOnly cookie, and never the
	 * response body.
	 *
	 * <p>
	 * It used to be serialised as well, which gave away the whole point of the
	 * cookie: any script on the frontend origin -- an XSS, a compromised npm
	 * dependency -- could read a 14-day credential out of the JSON instead of
	 * being limited to a 5-minute access token. Do not remove this annotation.
	 */
	@JsonIgnore
	String refreshToken;

}
