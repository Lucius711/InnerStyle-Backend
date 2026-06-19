-- Drop old constraint
ALTER TABLE dtb_meshy_tasks
DROP CONSTRAINT IF EXISTS chk_dtb_meshy_tasks_type;

-- Recreate with new enum values
ALTER TABLE dtb_meshy_tasks
ADD CONSTRAINT chk_dtb_meshy_tasks_type
CHECK (
    task_type IN (
        'IMAGE_TO_3D',
        'MULTI_IMAGE_TO_3D',
        'TEXT_TO_3D',
        'REFINE',
        'REMESH',
        'RETEXTURE',
        'RIG',
        'ANIMATE',
        'FIGURE_PROTOTYPE',
        'FIGURE_BUILD'
    )
);