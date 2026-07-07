package com.innerstyle.auth.config;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient} used to call the Resend REST API. The bearer token and base URL
 * come from {@link ResendProperties}. Kept as a separate qualified bean so it can be mocked in
 * tests and does not collide with other {@code RestClient} beans.
 */
@Configuration
public class ResendClientConfig {

    public static final String RESEND_REST_CLIENT = "resendApiRestClient";

    @Bean(RESEND_REST_CLIENT)
    public RestClient resendRestClient(ResendProperties properties) {
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(properties.connectTimeout())
            .withReadTimeout(properties.readTimeout());

        return RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(ClientHttpRequestFactories.get(settings))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + safeKey(properties))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    private String safeKey(ResendProperties properties) {
        // Allow the context to start without a key (e.g. tests); the sender is only selected
        // when a key is present, so this placeholder is never actually sent.
        return properties.hasApiKey() ? properties.apiKey() : "MISSING_RESEND_API_KEY";
    }
}
