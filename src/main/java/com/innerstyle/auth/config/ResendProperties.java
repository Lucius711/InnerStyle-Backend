package com.innerstyle.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Configuration for the Resend transactional-email HTTP API, bound from {@code app.resend.*}.
 * Delivery goes over HTTPS (port 443), so it works on cloud hosts that block outbound SMTP
 * (e.g. Railway on non-Pro plans). When {@code apiKey} is set, {@code EmailSenderConfig} selects
 * the Resend sender in preference to SMTP. The API key comes from an environment variable.
 */
@ConfigurationProperties(prefix = "app.resend")
public record ResendProperties(
    String apiKey,
    @DefaultValue("https://api.resend.com") String baseUrl,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("10s") Duration readTimeout
) {

    public boolean hasApiKey() {
        return StringUtils.hasText(apiKey);
    }
}
