package com.wtsend.backend.services;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.wtsend.backend.dto.TokenPayload;
import com.wtsend.backend.model.User;

@Service
public class JwtService {

	@Value("${jwt.access-token.ttl}")
	private Long accessTokenTTL;

	private final JwtEncoder jwtEncoder;

	private final JwtDecoder jwtDecoder;

	JwtService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
		this.jwtEncoder = jwtEncoder;
		this.jwtDecoder = jwtDecoder;
	}

	public String generateToken(User user) {

		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build();

		Instant now = Instant.now();

		JwtClaimsSet claimsSet = JwtClaimsSet.builder().issuer(user.getEmail()).subject(user.getId())
				.claim("email", user.getEmail())
				.claim("avt", user.getAvatarUrl() == null ? "" : user.getAvatarUrl())
				.claim("emv", user.isEmailVerified())
				.id(UUID.randomUUID().toString())
				.issuedAt(now)
				.expiresAt(now.plus(Duration.ofMinutes(accessTokenTTL))).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claimsSet)).getTokenValue();
	}

	public String generateToken(User user, Long tokenTTL) {

		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build();

		Instant now = Instant.now();

		JwtClaimsSet claimsSet = JwtClaimsSet.builder().issuer(user.getEmail()).subject(user.getId())
				.claim("email", user.getEmail())
				.claim("avt", user.getAvatarUrl() == null ? "" : user.getAvatarUrl())
				.claim("emv", user.isEmailVerified())
				.id(UUID.randomUUID().toString())
				.issuedAt(now)
				.expiresAt(now.plus(Duration.ofMinutes(tokenTTL))).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claimsSet)).getTokenValue();
	}

	public TokenPayload parseToken(String token) {
		Jwt jwt = jwtDecoder.decode(token);

		return TokenPayload.builder().jit(jwt.getId()).iss(null).sub(jwt.getSubject())
				.iat(jwt.getIssuedAt()).exp(jwt.getExpiresAt()).build();
	}
}
