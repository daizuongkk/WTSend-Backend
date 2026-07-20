package com.wtsend.backend.common.exception;

import lombok.Getter;

/**
 * The only exception the application throws deliberately.
 *
 * <p>
 * The client-facing message comes from the {@link ErrorCode}; {@code detail}
 * holds runtime context (ids, counters, upstream messages) and is written to
 * the server log only, never to the response.
 */
@Getter
public class AppException extends RuntimeException {

	private final transient ErrorCode errorCode;
	private String detail;

	public AppException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public AppException(ErrorCode errorCode, Throwable cause) {
		super(errorCode.getMessage(), cause);
		this.errorCode = errorCode;
	}

	/** Attaches log-only diagnostic context, e.g. {@code "userId=" + id}. */
	public AppException withDetail(String detail) {
		this.detail = detail;
		return this;
	}
}
