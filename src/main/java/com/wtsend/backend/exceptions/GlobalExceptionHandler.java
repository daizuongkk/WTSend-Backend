package com.wtsend.backend.exceptions;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.wtsend.backend.dtos.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<ApiResponse<Void>> handleForbiddenException(ForbiddenException ex,
			HttpServletRequest request) {
		ex.printStackTrace();
		ApiResponse<Void> response = ApiResponse.<Void>builder().success(false).message(ex.getMessage())
				.timestamp(LocalDateTime.now()).path(request.getRequestURI()).build();

		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
	}

	@ExceptionHandler(RequestException.class)
	public ResponseEntity<ApiResponse<Void>> handleFriendRequestException(RequestException ex,
			HttpServletRequest request) {
		ex.printStackTrace();
		ApiResponse<Void> response = ApiResponse.<Void>builder().success(false).message(ex.getMessage())
				.timestamp(LocalDateTime.now()).path(request.getRequestURI()).build();

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
	}

	@ExceptionHandler(RefreshTokenException.class)
	public ResponseEntity<ApiResponse<Void>> handleRefreshTokenException(RefreshTokenException ex,
			HttpServletRequest request) {
		ex.printStackTrace();
		ApiResponse<Void> response = ApiResponse.<Void>builder().success(false).message(ex.getMessage())
				.timestamp(LocalDateTime.now()).path(request.getRequestURI()).build();

		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
			ResourceNotFoundException ex,
			HttpServletRequest request) {

		ex.printStackTrace();
		ApiResponse<Void> response = ApiResponse.<Void>builder()
				.success(false)
				.message(ex.getMessage())
				.timestamp(LocalDateTime.now())
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
	}

	@ExceptionHandler(DuplicateResourceException.class)
	public ResponseEntity<ApiResponse<Void>> handleDuplicateResource(
			DuplicateResourceException ex,
			HttpServletRequest request) {

		ApiResponse<Void> response = ApiResponse.<Void>builder()
				.success(false)
				.message(ex.getMessage())
				.timestamp(LocalDateTime.now())
				.path(request.getRequestURI())
				.build();

		return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(
			MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		Map<String, String> errors = new HashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

		ApiResponse<Map<String, String>> response = ApiResponse.<Map<String, String>>builder()
				.success(false)
				.message("Validation failed")
				.data(errors)
				.timestamp(LocalDateTime.now())
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.badRequest().body(response);
	}

	@ExceptionHandler(UsernameNotFoundException.class)
	public ResponseEntity<ApiResponse<Map<String, String>>> handleUserNotFoundException(
			UsernameNotFoundException ex,
			HttpServletRequest request) {

		ApiResponse<Map<String, String>> response = ApiResponse.<Map<String, String>>builder()
				.success(false)
				.message("username or password incorrect")
				.data(null)
				.timestamp(LocalDateTime.now())
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<ApiResponse<Map<String, String>>> handleBadCredentialsException(
			BadCredentialsException ex,
			HttpServletRequest request) {

		ApiResponse<Map<String, String>> response = ApiResponse.<Map<String, String>>builder()
				.success(false)
				.message(ex.getLocalizedMessage())
				.data(null)
				.timestamp(LocalDateTime.now())
				.path(request.getRequestURI())
				.build();
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleGeneralException(
			Exception ex,
			HttpServletRequest request) {
		ex.printStackTrace();

		ApiResponse<Void> response = ApiResponse.<Void>builder()
				.success(false)
				.message(ex.getLocalizedMessage())
				.timestamp(LocalDateTime.now())
				.path(request.getRequestURI())
				.build();

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
	}
}