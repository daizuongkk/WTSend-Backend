package com.wtsend.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * A Facebook user access token from the JS SDK.
 *
 * <p>
 * Unlike Google, which is wired for the authorization-code flow, the
 * Facebook SDK hands the client a token directly -- so there is no code to
 * exchange and verification means asking Facebook about the token.
 */
public record FacebookAuthRequest(

    @NotBlank(message = "Facebook access token is required") String accessToken

) {
}