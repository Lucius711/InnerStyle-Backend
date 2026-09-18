-- Add expiry tracking and soft-delete support to meshy tasks.
-- expires_at: when Meshy CDN signed URLs become invalid (~14 days post-completion).
-- deleted_at: soft-delete marker; non-null rows are hidden from user library queries.
-- Also adds EXPIRED to the status check constraint.

ALTER TABLE dtb_meshy_tasks
    ADD COLUMN IF NOT EXISTS expires_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_at    TIMESTAMPTZ;

-- Update the status check constraint to include EXPIRED
ALTER TABLE dtb_meshy_tasks DROP CONSTRAINT IF EXISTS chk_dtb_meshy_tasks_status;
ALTER TABLE dtb_meshy_tasks
    ADD CONSTRAINT chk_dtb_meshy_tasks_status
    CHECK (status IN ('PENDING','IN_PROGRESS','SUCCEEDED','FAILED','CANCELED','EXPIRED'));

-- Index to support the "expiring soon" banner query and cleanup job
CREATE INDEX IF NOT EXISTS idx_dtb_meshy_tasks_expires_at
    ON dtb_meshy_tasks (expires_at)
    WHERE deleted_at IS NULL AND status = 'SUCCEEDED';

-- Index to fast-filter soft-deleted rows
CREATE INDEX IF NOT EXISTS idx_dtb_meshy_tasks_deleted_at
    ON dtb_meshy_tasks (deleted_at)
    WHERE deleted_at IS NOT NULL;
