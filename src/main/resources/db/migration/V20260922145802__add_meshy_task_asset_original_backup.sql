-- =============================================================================
-- "Auto-fix printability" edits the model in place with no way back (finding: the
-- repaired mesh can come out badly deformed, and the pre-repair mesh was gone for good).
-- Add nullable columns to hold ONE pristine backup per task, captured lazily the first
-- time that task is repaired, so a "revert to original" action has something to restore.
-- Conforms to rule 08 (migration) & 14 (schema).
-- =============================================================================

ALTER TABLE dtb_meshy_task_assets
    ADD COLUMN IF NOT EXISTS original_format       VARCHAR(8),
    ADD COLUMN IF NOT EXISTS original_content_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS original_data         BYTEA,
    ADD COLUMN IF NOT EXISTS original_size         BIGINT;
