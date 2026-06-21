package com.innerstyle.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * JWT settings bound from {@code app.jwt.*}. The secret MUST be overridden via the
 * {@code JWT_SECRET} env var in production (>= 32 bytes for HS256).
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret,
    @DefaultValue("innerstyle") String issuer,
    @DefaultValue("PT15M") Duration accessTtl,
    @DefaultValue("P7D") Duration refreshTtl
) {
}
