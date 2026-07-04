-- =============================================================================
-- Cache for a task's rendered preview image (thumbnail).
--
-- Meshy generates a thumbnail when the model is first created, but it is never
-- regenerated after an in-place edit (e.g. a custom base/stand added via
-- POST /tasks/{id}/base). So the editor captures a fresh PNG of the edited model
-- and uploads it here; the task's thumbnail_url then points at this stored image,
-- served same-origin via GET /tasks/{id}/thumbnail.
--
-- One thumbnail per task; shares the task's primary key. Conforms to rules 08 & 14.
-- =============================================================================

CREATE TABLE dtb_meshy_task_thumbnail (
    task_id    UUID PRIMARY KEY,
    data       BYTEA       NOT NULL,
    size       BIGINT      NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_dtb_meshy_task_thumbnail_task_id
        FOREIGN KEY (task_id) REFERENCES dtb_meshy_tasks (id) ON DELETE CASCADE
);
