package com.wtsend.backend.common.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	@ExceptionHandler(AppException.class)
	public ResponseEntity<ErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
		return respond(ex.getErrorCode(), request, ex, ex.getDetail());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		Map<String, String> errors = new HashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

		log.warn("[{}] {} {} -> {}", ErrorCode.VALIDATION_FAILED.getCode(), ErrorCode.VALIDATION_FAILED,
				request.getRequestURI(), errors);

		return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
				.body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, request.getRequestURI(), errors));
	}

	/** Violations on {@code @RequestParam} / {@code @PathVariable} (needs {@code @Validated}). */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) {
		Map<String, String> errors = new HashMap<>();
		ex.getConstraintViolations()
				.forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));

		log.warn("[{}] {} {} -> {}", ErrorCode.CONSTRAINT_VIOLATION.getCode(), ErrorCode.CONSTRAINT_VIOLATION,
				request.getRequestURI(), errors);

		return ResponseEntity.status(ErrorCode.CONSTRAINT_VIOLATION.getStatus())
				.body(ErrorResponse.of(ErrorCode.CONSTRAINT_VIOLATION, request.getRequestURI(), errors));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return respond(ErrorCode.ACCESS_DENIED, request, ex, null);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex,
			HttpServletRequest request) {
		return respond(ErrorCode.UNAUTHENTICATED, request, ex, null);
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex, HttpServletRequest request) {
		return respond(ErrorCode.MALFORMED_REQUEST, request, ex, null);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex,
			HttpServletRequest request) {
		return respond(ErrorCode.FILE_TOO_LARGE, request, ex, null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUncategorized(Exception ex, HttpServletRequest request) {
		return respond(ErrorCode.UNCATEGORIZED, request, ex, null);
	}

	/**
	 * Single exit point: the client only ever sees the ErrorCode's static
	 * message, while the stack trace and any diagnostic detail stay server-side.
	 * 5xx logs the cause, 4xx logs one line -- expected client errors should not
	 * fill the log with stack traces.
	 */
	private static ResponseEntity<ErrorResponse> respond(ErrorCode errorCode, HttpServletRequest request,
			Exception ex, String detail) {
		String path = request.getRequestURI();

		if (errorCode.getStatus().is5xxServerError()) {
			log.error("[{}] {} {} {}", errorCode.getCode(), errorCode, path, detail == null ? "" : detail, ex);
		} else {
			log.warn("[{}] {} {} {}", errorCode.getCode(), errorCode, path, detail == null ? "" : detail);
		}

		return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, path));
	}
}
