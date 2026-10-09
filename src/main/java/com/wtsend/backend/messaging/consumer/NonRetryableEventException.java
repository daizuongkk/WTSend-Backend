package com.wtsend.backend.messaging.consumer;

/**
 * This record will fail the same way however many times it is tried, so send
 * it straight to the dead-letter topic.
 *
 * <p>
 * It exists because the obvious shortcut is wrong: listing {@code AppException}
 * as non-retryable would send every transient Resend failure to the DLT, since
 * that one type covers both "no such user" and "the mail provider answered
 * 503". The consumer decides per {@code ErrorCode} and wraps only the
 * deterministic ones in this.
 */
public class NonRetryableEventException extends RuntimeException {

	public NonRetryableEventException(String message, Throwable cause) {
		super(message, cause);
	}
}
