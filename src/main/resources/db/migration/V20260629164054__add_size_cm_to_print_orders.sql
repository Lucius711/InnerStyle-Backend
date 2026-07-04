-- 3D-print orders are now priced by figurine height (cm). Store the chosen size on the order.
-- Conforms to rules 08 (migration) & 14 (schema). Nullable so existing rows stay valid.
ALTER TABLE dtb_print_orders ADD COLUMN size_cm INTEGER;
