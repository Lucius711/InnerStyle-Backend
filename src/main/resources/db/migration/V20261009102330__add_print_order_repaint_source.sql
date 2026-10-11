-- Staff AI repaint: when a repainted (Meshy Retexture) model is applied to an order, its previous
-- source task is kept here so the swap can be undone. NULL = no repaint applied.
ALTER TABLE dtb_print_orders
    ADD COLUMN repaint_source_task_id UUID,
    ADD CONSTRAINT fk_dtb_print_orders_repaint_source_task_id
        FOREIGN KEY (repaint_source_task_id) REFERENCES dtb_meshy_tasks (id) ON DELETE SET NULL;
