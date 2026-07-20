package com.wtsend.backend.common.exception;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Security rejects requests inside the filter chain, before any
 * {@code @RestControllerAdvice} runs -- so without this, 401s and 403s would
 * bypass {@link GlobalExceptionHandler} and return a container error page.
 *
 * <p>
 * Implements both halves: the entry point (not authenticated) and the access
 * denied handler (authenticated, but lacking the required authority).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ObjectMapper objectMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		write(request, response, ErrorCode.UNAUTHENTICATED, authException.getMessage());
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(request, response, ErrorCode.ACCESS_DENIED, accessDeniedException.getMessage());
	}

	private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode, String detail)
			throws IOException {
		String path = request.getRequestURI();
		// The upstream message can name the failing claim or authority, so it is
		// logged rather than returned.
		log.warn("[{}] {} {} {}", errorCode.getCode(), errorCode, path, detail == null ? "" : detail);

		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(errorCode, path));
	}
}
