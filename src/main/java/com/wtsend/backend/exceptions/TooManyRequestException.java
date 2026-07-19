package com.wtsend.backend.exceptions;

public class TooManyRequestException extends RuntimeException {
	public TooManyRequestException(String message) {
		super(message);
	}

	public TooManyRequestException(String message, Throwable cause) {
		super(message, cause);
	}
}
