package com.wtsend.backend.services;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.wtsend.backend.common.exception.AppException;
import com.wtsend.backend.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
// No interface, matching FacebookAuthService: the two providers verify in
// genuinely different ways -- Google exchanges an authorization code, Facebook
// inspects a token -- so a shared abstraction over them described nothing. The
// old one existed with a single implementation and nobody injecting it.
public class GoogleAuthService {

	@Value("${spring.security.oauth2.client.registration.google.client-id}")
	private String clientId;

	private final ClientRegistrationRepository clientRegistrationRepository;

	public String authenticate(String code) {
		ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("google");

		OAuth2AuthorizationRequest authRequest = OAuth2AuthorizationRequest.authorizationCode()
				.authorizationUri(registration.getProviderDetails().getAuthorizationUri())
				.clientId(registration.getClientId())
				.redirectUri("postmessage")
				.build();

		OAuth2AuthorizationResponse authResponse = OAuth2AuthorizationResponse.success(code)
				.redirectUri("postmessage")
				.build();

		OAuth2AuthorizationExchange exchange = new OAuth2AuthorizationExchange(authRequest, authResponse);
		OAuth2AuthorizationCodeGrantRequest grantRequest = new OAuth2AuthorizationCodeGrantRequest(registration, exchange);

		RestClientAuthorizationCodeTokenResponseClient tokenClient = new RestClientAuthorizationCodeTokenResponseClient();

		OAuth2AccessTokenResponse tokenResponse = tokenClient.getTokenResponse(grantRequest);

		return (String) tokenResponse.getAdditionalParameters().get("id_token");
	}

	public GoogleIdToken.Payload verify(String credential) {
		try {
			String idTokenValue = authenticate(credential);

			// An exchange that succeeded without returning an id_token: the cast in
			// authenticate() yields null and verify() then threw NPE as a 500.
			if (idTokenValue == null) {
				throw new AppException(ErrorCode.GOOGLE_TOKEN_INVALID)
						.withDetail("the code exchange returned no id_token");
			}

			GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
					new NetHttpTransport(), new GsonFactory())
					.setAudience(Collections.singletonList(clientId))
					.build();

			GoogleIdToken idToken = verifier.verify(idTokenValue);

			if (idToken == null) {
				throw new AppException(ErrorCode.GOOGLE_TOKEN_INVALID);
			}

			return idToken.getPayload();

			// Google refusing the authorization code -- expired, already spent, or
			// issued to a different client -- is a bad credential, not a fault on
			// this side. It is a RuntimeException, so it used to pass straight
			// through as a 500.
		} catch (OAuth2AuthorizationException e) {
			throw new AppException(ErrorCode.GOOGLE_TOKEN_INVALID, e);

			// Narrowed from catch(Exception): the previous wide catch forced a no-op
			// `catch (RuntimeException e) { throw e; }` above it just to let the
			// invalid-token case escape.
		} catch (GeneralSecurityException | IOException e) {
			throw new AppException(ErrorCode.GOOGLE_VERIFICATION_FAILED, e);
		}
	}

}
