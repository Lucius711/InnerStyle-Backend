-- =============================================================================
-- Harden payment settlement against double-fulfillment (review finding C1 / m1).
--   1. Optimistic-lock `version` column on payment orders (JPA @Version). Combined
--      with a pessimistic SELECT ... FOR UPDATE in the settle path, concurrent /
--      retried gateway callbacks for the same order can no longer both settle.
--   2. Partial UNIQUE index on (provider, provider_txn_ref): one gateway transaction
--      may settle at most one order (DB-enforced idempotency / replay defence).
-- Forward-only, immutable migration (rule 08).
-- =============================================================================

ALTER TABLE dtb_payment_orders
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- provider_txn_ref is NULL until a gateway confirms, so the uniqueness only applies
-- to confirmed transactions. Two different orders can never share one gateway txn ref.
CREATE UNIQUE INDEX uq_dtb_payment_orders_provider_txn_ref
    ON dtb_payment_orders (provider, provider_txn_ref)
    WHERE provider_txn_ref IS NOT NULL;
