-- =============================================================================
-- Cache for the iOS AR Quick Look file (USDZ).
--
-- iOS AR Quick Look only accepts a real, fetchable HTTPS URL ending in .usdz —
-- it cannot consume the in-browser blob: URL produced by three.js' USDZExporter.
-- Locally-edited models (uploaded files / a custom base added in place) also have
-- no Meshy-hosted USDZ to fall back to. So we cache the USDZ bytes the browser
-- builds and serve them same-origin via GET /tasks/{id}/model?format=usdz.
--
-- One USDZ per task; shares the task's primary key. Conforms to rules 08 & 14.
-- =============================================================================

CREATE TABLE dtb_meshy_task_usdz (
    task_id    UUID PRIMARY KEY,
    data       BYTEA       NOT NULL,
    size       BIGINT      NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_dtb_meshy_task_usdz_task_id
        FOREIGN KEY (task_id) REFERENCES dtb_meshy_tasks (id) ON DELETE CASCADE
);
