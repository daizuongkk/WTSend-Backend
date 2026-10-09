package com.wtsend.backend.services;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;
import com.wtsend.backend.dto.FacebookUser;

import lombok.extern.slf4j.Slf4j;

/**
 * Verifies a Facebook user access token and reads the profile behind it.
 *
 * <p>
 * Deliberately not modelled on {@link GoogleAuthService}: Google is wired for
 * the authorization-code flow and exchanges a code for an id_token, whereas the
 * Facebook SDK hands the client a token already. There is nothing to exchange,
 * so verification means asking Facebook about the token.
 */
@Service
@Slf4j
public class FacebookAuthService {

	private static final String GRAPH = "https://graph.facebook.com/v21.0";

	private final String appId;
	private final String appSecret;
	private final RestClient restClient;

	public FacebookAuthService(
			@Value("${spring.security.oauth2.client.registration.facebook.client-id}") String appId,
			@Value("${spring.security.oauth2.client.registration.facebook.client-secret}") String appSecret) {
		this.appId = appId;
		this.appSecret = appSecret;
		this.restClient = RestClient.create();
	}

	/**
	 * @throws AppException if the token is invalid, belongs to a different
	 *                      Facebook app, or the account has no email address
	 */
	public FacebookUser verify(String accessToken) {
		assertTokenBelongsToThisApp(accessToken);
		return fetchProfile(accessToken);
	}

	/**
	 * The step that cannot be skipped.
	 *
	 * <p>
	 * Calling {@code /me} with the token would happily return a profile for a
	 * token issued to <em>any</em> Facebook app -- so anyone able to create their
	 * own app could mint a token for a victim and sign in here as them.
	 * {@code /debug_token} is what reports which app the token was issued to, and
	 * it is authenticated with the app access token so only we can ask.
	 */
	private void assertTokenBelongsToThisApp(String accessToken) {
		Map<String, Object> data = debugToken(accessToken);

		if (!Boolean.TRUE.equals(data.get("is_valid")))
			throw new AppException(ErrorCode.FACEBOOK_TOKEN_INVALID).withDetail("is_valid=false");

		Object tokenAppId = data.get("app_id");
		if (tokenAppId == null || !appId.equals(tokenAppId.toString()))
			throw new AppException(ErrorCode.FACEBOOK_TOKEN_INVALID)
					.withDetail("token was issued to app " + tokenAppId);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> debugToken(String accessToken) {
		try {
			Map<String, Object> response = restClient.get()
					.uri(GRAPH + "/debug_token?input_token={token}&access_token={app}",
							accessToken, appId + "|" + appSecret)
					.retrieve()
					.body(Map.class);

			Object data = response == null ? null : response.get("data");
			if (!(data instanceof Map<?, ?> map))
				throw new AppException(ErrorCode.FACEBOOK_TOKEN_INVALID).withDetail("no data in debug_token");

			return (Map<String, Object>) map;
		} catch (AppException e) {
			throw e;
		} catch (Exception e) {
			// Facebook unreachable or answering with an error: that is our problem,
			// not a bad token, so it must not read as "invalid credentials".
			log.error("Could not reach Facebook to verify a token", e);
			throw new AppException(ErrorCode.FACEBOOK_VERIFICATION_FAILED, e);
		}
	}

	private FacebookUser fetchProfile(String accessToken) {
		Map<?, ?> me;
		try {
			me = restClient.get()
					.uri(GRAPH + "/me?fields=id,name,email,picture.type(large)&access_token={token}", accessToken)
					.retrieve()
					.body(Map.class);
		} catch (Exception e) {
			log.error("Could not read a Facebook profile", e);
			throw new AppException(ErrorCode.FACEBOOK_VERIFICATION_FAILED, e);
		}

		if (me == null || me.get("id") == null)
			throw new AppException(ErrorCode.FACEBOOK_TOKEN_INVALID).withDetail("no id in /me");

		Object email = me.get("email");
		if (email == null || email.toString().isBlank())
			throw new AppException(ErrorCode.FACEBOOK_EMAIL_REQUIRED)
					.withDetail("facebookId=" + me.get("id"));

		return new FacebookUser(
				me.get("id").toString(),
				me.get("name") == null ? null : me.get("name").toString(),
				email.toString(),
				pictureUrl(me.get("picture")));
	}

	/** Graph nests it as {@code picture.data.url}, and any level may be absent. */
	private String pictureUrl(Object picture) {
		if (!(picture instanceof Map<?, ?> wrapper))
			return null;
		if (!(wrapper.get("data") instanceof Map<?, ?> data))
			return null;

		Object url = data.get("url");
		return url == null ? null : url.toString();
	}
}
