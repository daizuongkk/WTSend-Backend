package com.wtsend.backend.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The single catalogue of failures this API can report.
 *
 * <p>
 * {@code code} is a stable contract with the frontend and must never be reused
 * or renumbered. {@code message} is what the client sees, so it stays static --
 * runtime context (ids, counters) belongs in
 * {@link AppException#withDetail(String)}, which is log-only.
 *
 * <p>
 * Codes are banded by domain so clients can range-check:
 * 1xxx generic/validation, 2xxx auth, 3xxx user, 4xxx friend,
 * 5xxx conversation & message, 6xxx email & OTP, 7xxx media.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// --- 1000-1099 generic ------------------------------------------------
	UNCATEGORIZED(1000, HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
	INVALID_REQUEST(1001, HttpStatus.BAD_REQUEST, "Invalid request"),
	MALFORMED_REQUEST(1002, HttpStatus.BAD_REQUEST, "Malformed request"),

	// --- 1100-1199 validation ---------------------------------------------
	VALIDATION_FAILED(1100, HttpStatus.BAD_REQUEST, "Validation failed"),
	CONSTRAINT_VIOLATION(1101, HttpStatus.BAD_REQUEST, "Constraint violation"),

	// --- 2000-2099 authentication & authorization -------------------------
	UNAUTHENTICATED(2000, HttpStatus.UNAUTHORIZED, "Authentication required"),
	INVALID_CREDENTIALS(2001, HttpStatus.UNAUTHORIZED, "Username or password incorrect"),
	ACCESS_DENIED(2002, HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
	INVALID_TOKEN(2003, HttpStatus.UNAUTHORIZED, "Token is invalid"),
	TOKEN_EXPIRED(2004, HttpStatus.UNAUTHORIZED, "Token has expired"),
	REFRESH_TOKEN_INVALID(2005, HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired"),
	GOOGLE_TOKEN_INVALID(2006, HttpStatus.UNAUTHORIZED, "Google token is invalid"),
	GOOGLE_VERIFICATION_FAILED(2007, HttpStatus.BAD_GATEWAY, "Could not verify the Google token"),
	EMAIL_NOT_VERIFIED(2008, HttpStatus.FORBIDDEN, "Email address is not verified"),
	NO_PASSWORD_SET(2009, HttpStatus.BAD_REQUEST, "Account has no password set"),
	WRONG_CURRENT_PASSWORD(2010, HttpStatus.BAD_REQUEST, "Current password is incorrect"),

	// --- 3000-3099 user ---------------------------------------------------
	USER_NOT_FOUND(3000, HttpStatus.NOT_FOUND, "User not found"),
	EMAIL_ALREADY_EXISTS(3001, HttpStatus.CONFLICT, "Email already exists"),
	USERNAME_ALREADY_EXISTS(3002, HttpStatus.CONFLICT, "Username already exists"),
	USERNAME_REQUIRED(3003, HttpStatus.BAD_REQUEST, "Username is required"),

	// --- 4000-4099 friends ------------------------------------------------
	FRIEND_REQUEST_NOT_FOUND(4000, HttpStatus.NOT_FOUND, "Friend request not found"),
	FRIEND_REQUEST_ALREADY_SENT(4001, HttpStatus.CONFLICT, "Friend request already sent"),
	ALREADY_FRIENDS(4002, HttpStatus.CONFLICT, "Already friends"),
	CANNOT_FRIEND_SELF(4003, HttpStatus.BAD_REQUEST, "Cannot send a friend request to yourself"),
	FRIEND_REQUEST_FORBIDDEN(4004, HttpStatus.FORBIDDEN, "You cannot act on this friend request"),
	NOT_FRIENDS(4005, HttpStatus.FORBIDDEN, "You are not friends with this user"),

	// --- 5000-5099 conversations & messages -------------------------------
	CONVERSATION_NOT_FOUND(5000, HttpStatus.NOT_FOUND, "Conversation not found"),
	MESSAGE_NOT_FOUND(5001, HttpStatus.NOT_FOUND, "Message not found"),
	CONVERSATION_INVALID_TYPE(5002, HttpStatus.BAD_REQUEST, "Conversation type is invalid for this operation"),
	CONVERSATION_ACCESS_DENIED(5003, HttpStatus.FORBIDDEN, "You are not a member of this conversation"),
	CANNOT_CONVERSE_WITH_SELF(5004, HttpStatus.BAD_REQUEST, "Cannot create a conversation with yourself"),
	CANNOT_CONVERSE_WITH_STRANGER(5005, HttpStatus.FORBIDDEN, "Cannot create a conversation with a non-friend"),
	PARTICIPANT_NOT_FOUND(5006, HttpStatus.NOT_FOUND, "Participant not found"),

	// --- 6000-6099 email & OTP --------------------------------------------
	EMAIL_SEND_FAILED(6000, HttpStatus.BAD_GATEWAY, "Could not send the email, please try again"),
	VERIFY_EMAIL_COOLDOWN(6001, HttpStatus.TOO_MANY_REQUESTS,
			"Please wait before requesting another verification email"),
	VERIFICATION_TOKEN_INVALID(6002, HttpStatus.BAD_REQUEST, "Verification token is invalid or expired"),
	OTP_EXPIRED(6003, HttpStatus.BAD_REQUEST, "OTP has expired or does not exist"),
	OTP_INCORRECT(6004, HttpStatus.BAD_REQUEST, "OTP code is incorrect"),
	OTP_MAX_ATTEMPTS(6005, HttpStatus.TOO_MANY_REQUESTS,
			"You have entered incorrectly too many times, please request a new OTP"),

	// --- 7000-7099 media --------------------------------------------------
	FILE_REQUIRED(7000, HttpStatus.BAD_REQUEST, "No file uploaded"),
	FILE_UPLOAD_FAILED(7001, HttpStatus.BAD_GATEWAY, "Could not upload the file"),
	FILE_TOO_LARGE(7002, HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file is too large");

	private final int code;
	private final HttpStatus status;
	private final String message;
}
