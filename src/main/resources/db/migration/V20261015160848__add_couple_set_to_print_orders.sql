-- Couple Set: one print order can carry a second of the buyer's models (its own height),
-- priced at both figurines minus app.print.couple-discount-percent. Nullable: single orders leave them empty.
ALTER TABLE dtb_print_orders ADD COLUMN pair_task_id UUID;
ALTER TABLE dtb_print_orders ADD COLUMN pair_size_cm INTEGER;
