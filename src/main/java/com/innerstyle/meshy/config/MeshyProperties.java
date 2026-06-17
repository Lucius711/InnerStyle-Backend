package com.innerstyle.meshy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Type-safe configuration for the MeshyAI integration, bound from {@code app.meshy.*}.
 * The API key and webhook secret come from environment variables (see {@code .env}).
 */
@ConfigurationProperties(prefix = "app.meshy")
public record MeshyProperties(
    String apiKey,
    @DefaultValue("https://api.meshy.ai") String baseUrl,
    String webhookSecret,
    @DefaultValue("10s") Duration connectTimeout,
    @DefaultValue("60s") Duration readTimeout,
    @DefaultValue Poll poll
) {

    /** Polling fallback settings (webhook is the primary completion path). */
    public record Poll(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("15000") long intervalMs,
        @DefaultValue("25") int batchSize
    ) {}

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
