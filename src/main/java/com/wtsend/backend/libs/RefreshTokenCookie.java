package com.wtsend.backend.libs;

import java.time.Duration;

import org.springframework.http.ResponseCookie;

/**
 * The one definition of the refresh-token cookie.
 *
 * <p>
 * The same builder was written out at five call sites. That matters beyond
 * duplication: a browser only replaces or deletes a cookie when the attributes
 * match, so a logout whose attributes had drifted from the login's would leave
 * the cookie in place -- which is a bug this codebase has already had once.
 *
 * <p>
 * {@code maxAge} is 7 days while {@code jwt.refresh-token.ttl} governs the
 * server-side lifetime, so the cookie can expire while the stored token is
 * still live. That direction is harmless -- the user simply has to sign in
 * again -- but the two are not derived from each other, and tightening the
 * config value will not shorten the cookie.
 */
public final class RefreshTokenCookie {

	public static final String NAME = "refreshToken";

	private static final Duration MAX_AGE = Duration.ofDays(7);

	private RefreshTokenCookie() {
	}

	public static ResponseCookie of(String token) {
		return base(token).maxAge(MAX_AGE).build();
	}

	/** Same attributes with a zero age, which is what makes the browser drop it. */
	public static ResponseCookie expired() {
		return base("").maxAge(0).build();
	}

	private static ResponseCookie.ResponseCookieBuilder base(String value) {
		return ResponseCookie.from(NAME, value)
				.httpOnly(true)
				.secure(true)
				.path("/")
				.sameSite("None");
	}
}
