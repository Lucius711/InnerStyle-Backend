package com.innerstyle.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Rate-limit settings bound from {@code app.rate-limit.*}. Counts are per rolling window
 * (fixed-window counter in Redis).
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("120") int defaultPerMinute,
    @DefaultValue("5") int loginPerMinute,
    @DefaultValue("5") int registerPerHour,
    @DefaultValue("3") int emailPer10min,
    @DefaultValue("10") int generationPerMinute,
    @DefaultValue("5") int paymentPerMinute
) {
}
