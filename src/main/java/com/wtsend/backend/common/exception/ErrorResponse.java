package com.wtsend.backend.common.exception;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The one shape every API error takes.
 *
 * <p>
 * {@code errors} carries per-field validation detail and is omitted entirely
 * when absent, so a plain failure matches the documented four-field format.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
		boolean success,
		int code,
		String message,
		Instant timestamp,
		String path,
		Map<String, String> errors) {

	public static ErrorResponse of(ErrorCode errorCode, String path) {
		return new ErrorResponse(false, errorCode.getCode(), errorCode.getMessage(), Instant.now(), path, null);
	}

	public static ErrorResponse of(ErrorCode errorCode, String path, Map<String, String> errors) {
		return new ErrorResponse(false, errorCode.getCode(), errorCode.getMessage(), Instant.now(), path, errors);
	}
}
