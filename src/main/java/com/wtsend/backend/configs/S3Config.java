package com.wtsend.backend.configs;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.util.Value;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

  @Value("${aws.region}")
  private String region;

  @Value("${aws.access-key-id}")
  private String accessKeyId;

  @Value("${aws.secret-access-key}")
  private String secretAccessKey;

  private static final long CONNECTION_TIMEOUT_SEC = 5;
  private static final long SOCKET_TIMEOUT_SEC = 5;
  private static final Integer MAX_CONNECTION = 50;

  @Bean
  public S3Client s3Client() {
    return S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
        .httpClient(Apache5HttpClient.builder()
            .connectionTimeout(Duration.ofSeconds(
                CONNECTION_TIMEOUT_SEC))
            .socketTimeout(Duration.ofSeconds(
                SOCKET_TIMEOUT_SEC))
            .maxConnections(
                MAX_CONNECTION)
            .build())
        .build();
  }

  @Bean
  public S3Presigner s3Presigner() {
    return S3Presigner.builder()
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
        .build();
  }
}