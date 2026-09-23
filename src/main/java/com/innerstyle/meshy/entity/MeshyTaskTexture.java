package com.innerstyle.meshy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Cached texture-map bytes for a task, bytes stored in Cloudflare R2 (row = key + metadata).
 *
 * <p>Meshy hosts texture images (base color, metallic, normal, ...) on its CDN behind
 * <b>presigned URLs that expire</b>. The same-origin texture proxy re-fetches that URL on every
 * call, so once the signature expires the CDN answers 403, the proxy fails, and the in-browser
 * viewer renders a grey (un-textured) model. Caching the bytes on first successful fetch makes
 * the proxy independent of URL expiry.
 *
 * <p>One row per {@code (taskId, mapName)}. {@link #sourceKey} holds the CDN URL path without its
 * query string (the signature) — a stable fingerprint of the underlying image. When a task is
 * re-textured the path changes, so a differing {@code sourceKey} marks the cache stale and forces
 * a refresh.
 */
@Entity
@Table(name = "dtb_meshy_task_textures")
@IdClass(MeshyTaskTextureId.class)
@Getter
@Setter
@NoArgsConstructor
public class MeshyTaskTexture {

    /** Same id as the owning {@link MeshyTask}. */
    @Id
    @Column(name = "task_id")
    private UUID taskId;

    /** Texture map name (e.g. {@code base_color}, {@code metallic}, {@code normal}). */
    @Id
    @Column(name = "map_name", length = 32)
    private String mapName;

    /** CDN URL path without query string — fingerprint that detects a re-textured image. */
    @Column(name = "source_key", nullable = false)
    private String sourceKey;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(nullable = false, length = 8)
    private String ext;

    /** Object key of the bytes in R2 (see {@code ObjectStorageService}). */
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(nullable = false)
    private long size;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
