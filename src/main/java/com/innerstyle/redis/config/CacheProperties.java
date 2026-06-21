package com.innerstyle.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Cache TTLs bound from {@code app.cache.*}.
 */
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(
    @DefaultValue("PT1H") Duration pricingTtl,
    @DefaultValue("PT60S") Duration galleryTtl,
    @DefaultValue("PT15M") Duration userTtl
) {
}
