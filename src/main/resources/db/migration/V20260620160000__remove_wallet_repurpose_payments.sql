-- =============================================================================
-- Remove the virtual wallet (storing user money requires an e-money licence in Vietnam).
-- Payments become direct: each payment funds a subscription or a print order. The existing
-- dtb_payment_orders + gateway infra is kept but repurposed (purpose + reference); the wallet,
-- holds, ledger and VND pricing tables are dropped.
-- =============================================================================

-- 1) Detach meshy tasks from holds (credits replace holds; user_id stays).
ALTER TABLE dtb_meshy_tasks DROP CONSTRAINT IF EXISTS fk_dtb_meshy_tasks_hold_id;
DROP INDEX IF EXISTS idx_dtb_meshy_tasks_hold_id;
ALTER TABLE dtb_meshy_tasks DROP COLUMN IF EXISTS hold_id;

-- 2) Repurpose payment orders: drop the wallet link, add purpose + reference.
ALTER TABLE dtb_payment_orders DROP CONSTRAINT IF EXISTS fk_dtb_payment_orders_wallet_id;
ALTER TABLE dtb_payment_orders DROP COLUMN IF EXISTS wallet_id;
-- Clear old wallet top-up orders (no longer meaningful).
DELETE FROM dtb_payment_orders;
ALTER TABLE dtb_payment_orders
    ADD COLUMN purpose   VARCHAR(20) NOT NULL DEFAULT 'PRINT',
    ADD COLUMN reference VARCHAR(64);
ALTER TABLE dtb_payment_orders
    ADD CONSTRAINT chk_dtb_payment_orders_purpose
    CHECK (purpose IN ('SUBSCRIPTION', 'PRINT'));

-- 3) Drop wallet tables (order matters: dependents first).
DROP TABLE IF EXISTS dtb_holds CASCADE;
DROP TABLE IF EXISTS dtb_wallet_transactions CASCADE;
DROP TABLE IF EXISTS dtb_wallets CASCADE;

-- 4) Drop the VND pricing table (3D ops are credit-based; the print fee comes from config).
DROP TABLE IF EXISTS mtb_pricing CASCADE;
