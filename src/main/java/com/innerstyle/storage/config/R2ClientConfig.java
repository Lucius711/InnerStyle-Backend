package com.innerstyle.storage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/** S3 client pointed at Cloudflare R2 (region {@code auto}, path-style addressing). */
@Configuration
public class R2ClientConfig {

    private static final Region R2_REGION = Region.of("auto");

    @Bean(destroyMethod = "close")
    public S3Client r2Client(StorageProperties properties) {
        StorageProperties.R2 r2 = properties.r2();
        // Resolved lazily on each request so the app still boots (e.g. in tests) without R2 creds.
        AwsCredentialsProvider credentials =
            () -> AwsBasicCredentials.create(r2.accessKeyId(), r2.secretAccessKey());
        return S3Client.builder()
            .endpointOverride(URI.create(r2.endpoint()))
            .region(R2_REGION)
            .credentialsProvider(credentials)
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            // R2 does not support every default checksum the newer AWS SDKs send.
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .build();
    }
}
