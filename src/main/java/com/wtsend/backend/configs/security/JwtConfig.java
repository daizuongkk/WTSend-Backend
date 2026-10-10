package com.wtsend.backend.configs.security;

import java.util.ArrayList;
import java.util.Collection;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
public class JwtConfig {

	private final RsaKeyConfig rsaKey;
	private final PasswordChangeCutoff passwordChangeCutoff;

	JwtConfig(RsaKeyConfig rsaKey, PasswordChangeCutoff passwordChangeCutoff) {
		this.rsaKey = rsaKey;
		this.passwordChangeCutoff = passwordChangeCutoff;
	}

	@Bean
	public JwtEncoder jwtEncoder() {
		JWK jwk = new RSAKey.Builder(rsaKey.getPublicKey()).privateKey(rsaKey.getPrivateKey()).build();
		JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));
		return new NimbusJwtEncoder(jwks);
	}

	/**
	 * Validates the signature and timestamps as before, and additionally rejects
	 * a token minted before its owner's last password change.
	 *
	 * <p>
	 * Without that last check, changing or resetting a password revoked only the
	 * refresh tokens. Access tokens are stateless and were accepted on signature
	 * and expiry alone, so whoever held a stolen one kept reading and writing for
	 * up to the full five minutes after the victim had already changed their
	 * password -- and changePassword made it worse by handing the caller a fresh
	 * pair while the attacker's old token also stayed live.
	 *
	 * <p>
	 * The cost, chosen deliberately: one Redis read per authenticated request.
	 */
	@Bean
	public JwtDecoder jwtDecoder() {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.getPublicKey()).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefault(),
				this::notIssuedBeforeAPasswordChange));
		return decoder;
	}

	private OAuth2TokenValidatorResult notIssuedBeforeAPasswordChange(Jwt jwt) {
		if (passwordChangeCutoff.stillValid(jwt.getSubject(), jwt.getIssuedAt()))
			return OAuth2TokenValidatorResult.success();

		return OAuth2TokenValidatorResult.failure(new OAuth2Error(
				"invalid_token", "Token was issued before the password changed", null));
	}

	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			Collection<GrantedAuthority> authorities = new ArrayList<>();

			Collection<GrantedAuthority> scopeAuthorities = scopes.convert(jwt);
			if (scopeAuthorities != null) {
				authorities.addAll(scopeAuthorities);
			}

			boolean verified = Boolean.TRUE.equals(
					jwt.getClaimAsBoolean("emv"));

			if (verified) {
				authorities.add(new SimpleGrantedAuthority("EMAIL_VERIFIED"));
			} else {
				authorities.add(new SimpleGrantedAuthority("EMAIL_NOT_VERIFIED"));
			}
			return authorities;
		});

		return converter;
	}
}
