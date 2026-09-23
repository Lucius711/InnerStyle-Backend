-- =============================================================================
-- Step 2 of the R2 move (step 1: V20260923064513): drop the legacy BYTEA columns.
--
-- Guard: refuse to run while any blob has not been copied to R2 yet. The copy is done
-- by the backfill runner that shipped in step 1 — deploy step 1 first, let it finish
-- (log "R2 backfill: moved N legacy blobs"), then deploy this. If the guard fires,
-- Flyway rolls back and nothing is dropped.
-- =============================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM dtb_meshy_task_assets
               WHERE (storage_key IS NULL AND data IS NOT NULL)
                  OR (original_storage_key IS NULL AND original_data IS NOT NULL))
       OR EXISTS (SELECT 1 FROM dtb_meshy_task_usdz      WHERE storage_key IS NULL AND data IS NOT NULL)
       OR EXISTS (SELECT 1 FROM dtb_meshy_task_thumbnail WHERE storage_key IS NULL AND data IS NOT NULL)
       OR EXISTS (SELECT 1 FROM dtb_meshy_task_textures  WHERE storage_key IS NULL AND data IS NOT NULL)
    THEN
        RAISE EXCEPTION 'Legacy BYTEA blobs not yet copied to R2 — run the previous release (backfill) first';
    END IF;
END $$;

ALTER TABLE dtb_meshy_task_assets
    DROP COLUMN data,
    DROP COLUMN original_data,
    ALTER COLUMN storage_key SET NOT NULL;

ALTER TABLE dtb_meshy_task_usdz
    DROP COLUMN data,
    ALTER COLUMN storage_key SET NOT NULL;

ALTER TABLE dtb_meshy_task_thumbnail
    DROP COLUMN data,
    ALTER COLUMN storage_key SET NOT NULL;

ALTER TABLE dtb_meshy_task_textures
    DROP COLUMN data,
    ALTER COLUMN storage_key SET NOT NULL;

-- DROP COLUMN does not return disk space. Reclaim it manually, off-peak (takes an
-- exclusive lock per table; not done here because VACUUM cannot run in a transaction):
--   VACUUM FULL dtb_meshy_task_assets, dtb_meshy_task_usdz,
--               dtb_meshy_task_thumbnail, dtb_meshy_task_textures;
