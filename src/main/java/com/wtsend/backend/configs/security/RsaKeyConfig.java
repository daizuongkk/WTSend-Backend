package com.wtsend.backend.configs.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "rsa")
public class RsaKeyConfig {
	private RSAPublicKey publicKey;
	private RSAPrivateKey privateKey;
}
