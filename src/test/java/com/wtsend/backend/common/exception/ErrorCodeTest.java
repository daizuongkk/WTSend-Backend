package com.wtsend.backend.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

	/** Codes are a wire contract: a duplicate would make two failures indistinguishable. */
	@Test
	void everyCodeIsUnique() {
		Set<Integer> seen = new HashSet<>();
		Set<Integer> duplicates = Arrays.stream(ErrorCode.values())
				.map(ErrorCode::getCode)
				.filter(code -> !seen.add(code))
				.collect(Collectors.toSet());

		assertThat(duplicates).isEmpty();
	}

	@Test
	void everyCodeHasAStatusAndANonBlankMessage() {
		for (ErrorCode errorCode : ErrorCode.values()) {
			assertThat(errorCode.getStatus()).as("%s status", errorCode).isNotNull();
			assertThat(errorCode.getMessage()).as("%s message", errorCode).isNotBlank();
		}
	}

	/** Detail is diagnostic context and must never reach the client-facing message. */
	@Test
	void detailStaysOutOfTheClientMessage() {
		AppException ex = new AppException(ErrorCode.USER_NOT_FOUND).withDetail("id=secret-user-123");

		assertThat(ex.getMessage()).isEqualTo(ErrorCode.USER_NOT_FOUND.getMessage());
		assertThat(ex.getMessage()).doesNotContain("secret-user-123");
		assertThat(ex.getDetail()).isEqualTo("id=secret-user-123");

		assertThat(ErrorResponse.of(ErrorCode.USER_NOT_FOUND, "/api/users/1").message())
				.doesNotContain("secret-user-123");
	}
}
