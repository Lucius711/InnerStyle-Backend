-- The staff AI-repaint feature was removed (V20261009102330 added this column for it).
ALTER TABLE dtb_print_orders
    DROP CONSTRAINT IF EXISTS fk_dtb_print_orders_repaint_source_task_id,
    DROP COLUMN IF EXISTS repaint_source_task_id;
