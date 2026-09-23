package com.innerstyle.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Object storage (Cloudflare R2, S3-compatible) settings, bound from {@code app.storage.*}.
 * Large binary assets (GLB/USDZ models, texture maps, thumbnails) live in the bucket;
 * PostgreSQL only keeps the object key plus metadata (size, content type).
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
    @DefaultValue R2 r2
) {

    /** R2 connection settings. Endpoint: {@code https://<account-id>.r2.cloudflarestorage.com}. */
    public record R2(
        String endpoint,
        String accessKeyId,
        String secretAccessKey,
        @DefaultValue("innerstyle-assets") String bucket
    ) {}
}
