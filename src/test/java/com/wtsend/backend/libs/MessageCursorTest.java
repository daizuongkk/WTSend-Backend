package com.wtsend.backend.libs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;

class MessageCursorTest {

	@Test
	void aCursorRoundTripsAtFullPrecision() {
		// Microsecond precision has to survive: MySQL stores datetime(6), and
		// losing the sub-second part would make every message in the same second
		// tie, which is exactly what the id is there to break.
		Instant createdAt = Instant.parse("2026-10-08T09:41:23.123456Z");

		MessageCursor parsed = MessageCursor.parse(new MessageCursor(createdAt, 4321L).encode());

		assertThat(parsed.createdAt()).isEqualTo(createdAt);
		assertThat(parsed.id()).isEqualTo(4321L);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void aBlankCursorMeansTheFirstPage(String cursor) {
		// The client sends cursor= for the first page.
		assertThat(MessageCursor.parse(cursor)).isNull();
	}

	@Test
	void aNullCursorMeansTheFirstPage() {
		assertThat(MessageCursor.parse(null)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"not-base64-at-all!!",
			"bm8tc2VwYXJhdG9y", // valid base64, no separator
			"MjAyNi0xMC0wOFQwOTo0MToyM1p8bm90LWEtbnVtYmVy", // non-numeric id
			"bm90LWEtdGltZXN0YW1wfDQy" // unparseable timestamp
	})
	void aMalformedCursorIsRejectedAsMalformedRequest(String cursor) {
		assertThatThrownBy(() -> MessageCursor.parse(cursor))
				.isInstanceOf(AppException.class)
				.extracting(ex -> ((AppException) ex).getErrorCode())
				.isEqualTo(ErrorCode.MALFORMED_REQUEST);
	}

	@Test
	void theEncodingIsOpaqueToTheClient() {
		// Clients must not parse it: the sort key has to stay free to change.
		assertThat(new MessageCursor(Instant.parse("2026-10-08T09:41:23Z"), 7L).encode())
				.doesNotContain("2026", "|", "=");
	}
}
