package com.wtsend.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * An OAuth2 authorization code, not an ID token.
 *
 * <p>
 * The client uses Google Identity Services with {@code flow: "auth-code"},
 * and {@code GoogleAuthService} exchanges this for tokens against the
 * {@code postmessage} redirect URI. It was called {@code credential}, the
 * name GIS uses for the ID token in its other flow, which reads as though
 * the wrong thing is being sent here.
 */
public record GoogleAuthRequest(

    @NotBlank(message = "Authorization code is required") String code

) {
}