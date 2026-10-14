-- Print orders now record the chosen plastic (PLA / PETG / RESIN), which adds a surcharge to the size price.
-- Nullable so existing rows (placed before material selection existed) stay valid.
ALTER TABLE dtb_print_orders ADD COLUMN material VARCHAR(20);
ALTER TABLE dtb_print_orders ADD CONSTRAINT chk_dtb_print_orders_material
    CHECK (material IS NULL OR material IN ('PLA', 'PETG', 'RESIN'));
