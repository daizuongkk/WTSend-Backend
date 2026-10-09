package com.wtsend.backend.dto;

/**
 * The fields read back from Facebook's Graph API.
 *
 * <p>
 * {@code email} is nullable: an account registered with a phone number, or one
 * that declined the email permission, has none. The app keys identity on email,
 * so {@code FacebookAuthService} rejects that rather than inventing one.
 */
public record FacebookUser(String id, String name, String email, String pictureUrl) {
}
