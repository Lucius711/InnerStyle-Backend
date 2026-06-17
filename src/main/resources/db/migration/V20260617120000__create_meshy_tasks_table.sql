-- MeshyAI 3D generation tasks (image-to-3D, text-to-3D, remesh, retexture, rig, animate).
CREATE TABLE dtb_meshy_tasks (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meshy_task_id    VARCHAR(64)  NOT NULL,
    task_type        VARCHAR(32)  NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    progress         INTEGER      NOT NULL DEFAULT 0,
    parent_id        UUID,
    prompt           TEXT,
    source_image_url TEXT,
    model_urls       JSONB,
    texture_urls     JSONB,
    animation_urls   JSONB,
    thumbnail_url    TEXT,
    error_message    TEXT,
    consumed_credits INTEGER,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dtb_meshy_tasks_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SUCCEEDED', 'FAILED', 'CANCELED')),
    CONSTRAINT chk_dtb_meshy_tasks_type
        CHECK (task_type IN ('IMAGE_TO_3D', 'TEXT_TO_3D_PREVIEW', 'TEXT_TO_3D_REFINE',
                             'REMESH', 'RETEXTURE', 'RIG', 'ANIMATE')),
    CONSTRAINT fk_dtb_meshy_tasks_parent_id
        FOREIGN KEY (parent_id) REFERENCES dtb_meshy_tasks (id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX idx_dtb_meshy_tasks_meshy_task_id ON dtb_meshy_tasks (meshy_task_id);
CREATE INDEX idx_dtb_meshy_tasks_status ON dtb_meshy_tasks (status);
CREATE INDEX idx_dtb_meshy_tasks_parent_id ON dtb_meshy_tasks (parent_id);
CREATE INDEX idx_dtb_meshy_tasks_created_at ON dtb_meshy_tasks (created_at DESC);
