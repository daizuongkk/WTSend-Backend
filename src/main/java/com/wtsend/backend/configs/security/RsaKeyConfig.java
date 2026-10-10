package com.wtsend.backend.configs.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * {@code @Getter @Setter} rather than {@code @Data}: that would generate a
 * {@code toString()} covering the private key, and anything that logs this
 * bean -- a binding failure, a debug dump, an actuator endpoint -- would print
 * it.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "rsa")
public class RsaKeyConfig {
	private RSAPublicKey publicKey;
	private RSAPrivateKey privateKey;
}
