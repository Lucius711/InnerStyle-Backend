package com.innerstyle.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Auth-flow settings bound from {@code app.auth.*}.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
    @DefaultValue("http://localhost:5173") String frontendBaseUrl,
    /** Current Terms &amp; Policies version; bump it (e.g. to the publish date) to make every user re-accept. */
    @DefaultValue("2026-09-25") String policyVersion
) {
}
