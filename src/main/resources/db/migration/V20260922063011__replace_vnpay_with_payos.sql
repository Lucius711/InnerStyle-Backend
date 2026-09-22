-- =============================================================================
-- Switch the direct-payment gateway from VNPay to payOS (VNPay decommissioned).
-- Any existing VNPAY rows (dev/sandbox data only) are relabelled PAYOS so history
-- stays queryable; the provider CHECK constraints are swapped in place.
-- Forward-only, immutable migration (rule 08).
-- =============================================================================

ALTER TABLE dtb_payment_orders DROP CONSTRAINT chk_dtb_payment_orders_provider;
UPDATE dtb_payment_orders SET provider = 'PAYOS' WHERE provider = 'VNPAY';
ALTER TABLE dtb_payment_orders
    ADD CONSTRAINT chk_dtb_payment_orders_provider CHECK (provider IN ('PAYOS', 'MOMO'));

ALTER TABLE dtb_payment_callbacks DROP CONSTRAINT chk_dtb_payment_callbacks_provider;
UPDATE dtb_payment_callbacks SET provider = 'PAYOS' WHERE provider = 'VNPAY';
ALTER TABLE dtb_payment_callbacks
    ADD CONSTRAINT chk_dtb_payment_callbacks_provider CHECK (provider IN ('PAYOS', 'MOMO'));
