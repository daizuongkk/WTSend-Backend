package com.wtsend.backend.dto.request;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A partial update: {@code null} means "leave this field alone", which is how
 * {@code UserService.updateUser} has always read it.
 *
 * <p>
 * The fields used to be {@code @NotBlank}/{@code @NotEmpty}, which made every
 * one of them mandatory on every call and so made partial updates impossible.
 *
 * <p>
 * Dropping those constraints alone would let blank values through.
 * {@code @Pattern} rejects a present-but-blank value while still allowing
 * {@code null}, as Jakarta Bean Validation skips null values.
 *
 * <p>
 * There is deliberately no email field. Changing an address dropped the account
 * back to unverified and introduced security and account-cleanup concerns.
 */
public record UpdateUserRequest(

    @Size(min = 8, max = 20, message = "Username must be from 8 to 20 characters.") String username,

    MultipartFile avatar,

    /**
     * Present, and not just whitespace. Skipped entirely when the field is null.
     */
    @Pattern(regexp = "(?s).*\\S.*", message = "Display name cannot be blank") String displayName,

    /**
     * Empty clears the stored date, matching how {@code phone} behaves.
     */
    @Pattern(regexp = "^$|^(0[1-9]|[12]\\d|3[01])/(0[1-9]|1[012])/(19|20)\\d{2}$", message = "Invalid date format, expected dd/MM/yyyy") String birthday,

    @Pattern(regexp = "^$|^\\d{10,15}$", message = "Số điện thoại không hợp lệ") String phone,

    @Size(max = 200, message = "The maximum bio length is 200 characters.") String bio,

    /**
     * Boxed to distinguish null from false during partial updates.
     */
    Boolean showOnlineStatus

) {
}