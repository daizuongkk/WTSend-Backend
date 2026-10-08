package com.wtsend.backend.dto.request;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * A partial update: {@code null} means "leave this field alone", which is how
 * {@code UserService.updateUser} has always read it.
 *
 * <p>
 * The fields used to be {@code @NotBlank}/{@code @NotEmpty}, which made every
 * one of them mandatory on every call and so made partial updates impossible --
 * a client wanting to change only a bio had to resend username, email and
 * display name as well.
 *
 * <p>
 * Dropping those constraints alone would let a blank value through, and the
 * service would happily store it: {@code @Email} treats {@code ""} as valid per
 * the Jakarta spec, and {@code displayName} would have had nothing guarding it
 * at all. Both columns are NOT NULL and neither field is meant to be clearable,
 * so {@link #NOT_BLANK} rejects a present-but-blank value while still skipping
 * {@code null} the way every constraint does.
 *
 * <p>
 * There is deliberately no email field. Changing an address dropped the account
 * back to unverified, which made "unverified" mean two different things and put
 * long-standing accounts in scope for the 24-hour cleanup of abandoned
 * registrations. It also let anyone move their account onto somebody else's
 * address, which is exactly what that cleanup exists to prevent.
 */
@Getter
@Setter
public class UpdateUserRequest {

	/** Present, and not just whitespace. Skipped entirely when the field is null. */
	private static final String NOT_BLANK = "\\S.*";

	@Size(min = 8, max = 20, message = "Username must be from 8 to 20 characters.")
	private String username;

	private MultipartFile avatar;

	@Pattern(regexp = NOT_BLANK, message = "Display name cannot be blank")
	private String displayName;

	/**
	 * Empty clears the stored date, matching how {@code phone} already behaves.
	 * It previously had no empty branch, so a birthday could be set but never
	 * removed.
	 */
	@Pattern(regexp = "^$|^(0[1-9]|[12]\\d|3[01])/(0[1-9]|1[012])/(19|20)\\d{2}$", message = "Invalid date format, expected dd/MM/yyyy")
	private String birthday;

	@Pattern(regexp = "^$|^[0-9]{10,15}$", message = "Số điện thoại không hợp lệ")
	private String phone;

	@Size(max = 200, message = "The maximum bio length is 200 characters.")
	private String bio;
}
