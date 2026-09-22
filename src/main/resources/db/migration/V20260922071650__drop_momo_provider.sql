-- =============================================================================
-- MoMo is decommissioned; payOS is the only supported gateway. Relabel any
-- leftover MOMO rows (dev/sandbox data only) and tighten the provider CHECK
-- constraints to PAYOS-only.
-- Forward-only, immutable migration (rule 08).
-- =============================================================================

ALTER TABLE dtb_payment_orders DROP CONSTRAINT chk_dtb_payment_orders_provider;
UPDATE dtb_payment_orders SET provider = 'PAYOS' WHERE provider = 'MOMO';
ALTER TABLE dtb_payment_orders
    ADD CONSTRAINT chk_dtb_payment_orders_provider CHECK (provider = 'PAYOS');

ALTER TABLE dtb_payment_callbacks DROP CONSTRAINT chk_dtb_payment_callbacks_provider;
UPDATE dtb_payment_callbacks SET provider = 'PAYOS' WHERE provider = 'MOMO';
ALTER TABLE dtb_payment_callbacks
    ADD CONSTRAINT chk_dtb_payment_callbacks_provider CHECK (provider = 'PAYOS');
