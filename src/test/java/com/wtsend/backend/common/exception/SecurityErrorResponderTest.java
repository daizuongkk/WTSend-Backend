package com.wtsend.backend.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Both halves used to be unwired: 401s fell back to sendError and 403s -- the
 * common case, a failed EMAIL_VERIFIED check -- returned the container's error
 * page, so a client could not read an ErrorCode off a rejected request.
 */
class SecurityErrorResponderTest {

	// Mirrors the mapper Boot auto-configures: ErrorResponse.timestamp is an
	// Instant, which a bare ObjectMapper cannot serialize.
	private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
	private SecurityErrorResponder responder;
	private MockHttpServletRequest request;
	private MockHttpServletResponse response;

	@BeforeEach
	void setUp() {
		responder = new SecurityErrorResponder(objectMapper);
		request = new MockHttpServletRequest("GET", "/api/conversations");
		response = new MockHttpServletResponse();
	}

	@Test
	void anUnauthenticatedRequestGetsTheErrorResponseShape() throws Exception {
		responder.commence(request, response, new InsufficientAuthenticationException("no token"));

		assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
		assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);

		JsonNode body = objectMapper.readTree(response.getContentAsString());
		assertThat(body.get("success").asBoolean()).isFalse();
		assertThat(body.get("code").asInt()).isEqualTo(ErrorCode.UNAUTHENTICATED.getCode());
		assertThat(body.get("path").asText()).isEqualTo("/api/conversations");
	}

	@Test
	void aForbiddenRequestGetsTheErrorResponseShape() throws Exception {
		responder.handle(request, response, new AccessDeniedException("missing EMAIL_VERIFIED"));

		assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
		JsonNode body = objectMapper.readTree(response.getContentAsString());
		assertThat(body.get("code").asInt()).isEqualTo(ErrorCode.ACCESS_DENIED.getCode());
	}

	@Test
	void theUpstreamMessageIsNotLeakedToTheClient() throws Exception {
		// The upstream text can name the failing claim or authority, so the body
		// carries only the static ErrorCode message.
		responder.handle(request, response, new AccessDeniedException("missing EMAIL_VERIFIED"));

		assertThat(response.getContentAsString()).doesNotContain("EMAIL_VERIFIED");
		assertThat(objectMapper.readTree(response.getContentAsString()).get("message").asText())
				.isEqualTo(ErrorCode.ACCESS_DENIED.getMessage());
	}
}
