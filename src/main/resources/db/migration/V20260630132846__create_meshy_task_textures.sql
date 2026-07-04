-- =============================================================================
-- Cache for a task's texture maps (base_color / metallic / normal / ...).
--
-- Meshy hosts texture images on its CDN behind PRESIGNED URLs that EXPIRE. The
-- same-origin proxy (GET /tasks/{id}/texture) re-fetches that URL on every call,
-- so once the signature expires the CDN returns 403, the proxy fails, and the
-- in-browser viewer falls back to a grey (un-textured) model. Caching the bytes
-- here on first successful fetch makes the proxy independent of URL expiry.
--
-- One row per (task, map). `source_key` is the CDN URL path WITHOUT its query
-- string (the signature) — a stable fingerprint of the underlying image. When a
-- task is re-textured the path changes, so a differing source_key tells the proxy
-- the cache is stale and must be refreshed. Conforms to rules 08 & 14.
-- =============================================================================

CREATE TABLE dtb_meshy_task_textures (
    task_id      UUID         NOT NULL,
    map_name     VARCHAR(32)  NOT NULL,
    source_key   TEXT         NOT NULL,
    content_type VARCHAR(64)  NOT NULL,
    ext          VARCHAR(8)   NOT NULL,
    data         BYTEA        NOT NULL,
    size         BIGINT       NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dtb_meshy_task_textures PRIMARY KEY (task_id, map_name),
    CONSTRAINT fk_dtb_meshy_task_textures_task_id
        FOREIGN KEY (task_id) REFERENCES dtb_meshy_tasks (id) ON DELETE CASCADE
);
