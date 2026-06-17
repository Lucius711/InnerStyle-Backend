package com.innerstyle.meshy.config;

import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient} used to call the MeshyAI REST API. The bearer token and
 * base URL come from {@link MeshyProperties}. This bean is easily replaced/mocked in tests.
 */
@Configuration
public class MeshyClientConfig {

    public static final String MESHY_REST_CLIENT = "meshyApiRestClient";

    @Bean(MESHY_REST_CLIENT)
    public RestClient meshyRestClient(MeshyProperties properties) {
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

    private String safeKey(MeshyProperties properties) {
        // Allow the context to start without a key (e.g. tests); calls fail clearly at runtime.
        return properties.hasApiKey() ? properties.apiKey() : "MISSING_MESHY_API_KEY";
    }
}
