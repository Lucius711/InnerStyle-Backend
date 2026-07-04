-- =============================================================================
-- 1) Allow the new UPLOADED task type (user-imported 3D files).
-- 2) Store local model bytes out-of-line (uploaded files + in-place edits like a
--    custom base). One asset per task; shares the task's primary key.
-- Conforms to rules 08 (migration) & 14 (schema).
-- =============================================================================

ALTER TABLE dtb_meshy_tasks
    DROP CONSTRAINT IF EXISTS chk_dtb_meshy_tasks_type;

ALTER TABLE dtb_meshy_tasks
    ADD CONSTRAINT chk_dtb_meshy_tasks_type
    CHECK (
        task_type IN (
            'IMAGE_TO_3D',
            'MULTI_IMAGE_TO_3D',
            'TEXT_TO_3D_PREVIEW',
            'TEXT_TO_3D_REFINE',
            'REMESH',
            'RETEXTURE',
            'RIG',
            'ANIMATE',
            'FIGURE_PROTOTYPE',
            'FIGURE_BUILD',
            'UPLOADED'
        )
    );

CREATE TABLE dtb_meshy_task_assets (
    task_id      UUID PRIMARY KEY,
    format       VARCHAR(8)  NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    data         BYTEA       NOT NULL,
    size         BIGINT      NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_dtb_meshy_task_assets_task_id
        FOREIGN KEY (task_id) REFERENCES dtb_meshy_tasks (id) ON DELETE CASCADE
);
