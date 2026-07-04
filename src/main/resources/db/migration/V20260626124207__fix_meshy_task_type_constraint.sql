-- =============================================================================
-- Fix the dtb_meshy_tasks task_type CHECK constraint.
--
-- A previous migration (V20260619120000) recreated the constraint with the
-- values 'TEXT_TO_3D' and 'REFINE', but the MeshyTaskType enum actually
-- persists 'TEXT_TO_3D_PREVIEW' and 'TEXT_TO_3D_REFINE'. As a result, inserting
-- a text-to-3D task failed with:
--   new row ... violates check constraint "chk_dtb_meshy_tasks_type"
--
-- This migration drops and recreates the constraint so its allowed values match
-- the enum exactly. Conforms to rules 08 (migration) & 14 (schema).
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
            'FIGURE_BUILD'
        )
    );
