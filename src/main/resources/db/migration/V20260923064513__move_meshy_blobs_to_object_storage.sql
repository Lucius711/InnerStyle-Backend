-- =============================================================================
-- Move large 3D binaries (models, USDZ, thumbnails, texture maps) out of PostgreSQL
-- into Cloudflare R2. The DB keeps only the object key + metadata (size, content type).
--
-- Step 1 (this migration): add the key columns and relax NOT NULL on the legacy BYTEA
-- columns, so new rows carry only a key. Existing rows are copied to R2 at startup by
-- MeshyBlobBackfillRunner, which sets storage_key and NULLs the BYTEA per row.
-- Step 2 (a later migration, once the backfill has finished everywhere): DROP the
-- legacy data/original_data columns. Not done here — it would destroy un-copied blobs.
-- =============================================================================

ALTER TABLE dtb_meshy_task_assets
    ADD COLUMN IF NOT EXISTS storage_key          TEXT,
    ADD COLUMN IF NOT EXISTS original_storage_key TEXT,
    ALTER COLUMN data DROP NOT NULL;

ALTER TABLE dtb_meshy_task_usdz
    ADD COLUMN IF NOT EXISTS storage_key TEXT,
    ALTER COLUMN data DROP NOT NULL;

ALTER TABLE dtb_meshy_task_thumbnail
    ADD COLUMN IF NOT EXISTS storage_key TEXT,
    ALTER COLUMN data DROP NOT NULL;

ALTER TABLE dtb_meshy_task_textures
    ADD COLUMN IF NOT EXISTS storage_key TEXT,
    ALTER COLUMN data DROP NOT NULL;
