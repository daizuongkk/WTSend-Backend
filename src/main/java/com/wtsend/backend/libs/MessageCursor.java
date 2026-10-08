package com.wtsend.backend.libs;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;

/**
 * Keyset position in a conversation's history.
 *
 * <p>
 * Ordering by {@code createdAt} alone is not a total order: two messages saved
 * in the same microsecond tie, and a {@code createdAt < cursor} page boundary
 * then either skips the tied rows or returns them twice. The id breaks the tie,
 * so the cursor has to carry both halves.
 *
 * <p>
 * Encoded opaquely on purpose -- clients echo it back untouched, which leaves
 * the sort key free to change without breaking them. Instant is stored as its
 * ISO-8601 text so it round-trips at full precision with no unit arithmetic.
 */
public record MessageCursor(Instant createdAt, Long id) {

	private static final String SEPARATOR = "|";

	public String encode() {
		String raw = createdAt.toString() + SEPARATOR + id;
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * @param encoded cursor from a previous page; {@code null} or blank means
	 *                "start at the newest message" -- the client sends
	 *                {@code cursor=} for the first page.
	 * @return the decoded position, or {@code null} for the first page
	 */
	public static MessageCursor parse(String encoded) {
		if (encoded == null || encoded.isBlank())
			return null;

		try {
			String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			int separator = raw.lastIndexOf(SEPARATOR);
			if (separator < 0)
				throw new AppException(ErrorCode.MALFORMED_REQUEST).withDetail("cursor=" + encoded);

			return new MessageCursor(
					Instant.parse(raw.substring(0, separator)),
					Long.valueOf(raw.substring(separator + 1)));
		} catch (IllegalArgumentException | DateTimeParseException e) {
			// Covers bad base64, a non-numeric id and an unparseable timestamp.
			throw new AppException(ErrorCode.MALFORMED_REQUEST).withDetail("cursor=" + encoded);
		}
	}
}
