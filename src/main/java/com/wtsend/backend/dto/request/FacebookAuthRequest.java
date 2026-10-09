package com.wtsend.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacebookAuthRequest {

	/**
	 * A Facebook user access token from the JS SDK.
	 *
	 * <p>
	 * Unlike Google, which is wired for the authorization-code flow, the
	 * Facebook SDK hands the client a token directly -- so there is no code to
	 * exchange and verification means asking Facebook about the token.
	 */
	@NotBlank(message = "Facebook access token is required")
	private String accessToken;
}
