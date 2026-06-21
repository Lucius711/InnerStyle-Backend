-- 3D-print ordering: a flat fee (PRINT_3D price) is charged from the wallet when the user
-- places a print order for a finished model. Adds: PRINT_3D price, a PURCHASE ledger type,
-- and the dtb_print_orders table.

-- Price for the print service (reuses the pricing table).
ALTER TABLE mtb_pricing DROP CONSTRAINT IF EXISTS chk_mtb_pricing_task_type;
ALTER TABLE mtb_pricing
    ADD CONSTRAINT chk_mtb_pricing_task_type
    CHECK (task_type IN ('IMAGE_TO_3D', 'MULTI_IMAGE_TO_3D', 'TEXT_TO_3D_PREVIEW',
                         'TEXT_TO_3D_REFINE', 'REMESH', 'RETEXTURE', 'RIG', 'ANIMATE',
                         'FIGURE_PROTOTYPE', 'FIGURE_BUILD', 'PRINT_3D'));

INSERT INTO mtb_pricing (task_type, unit_price) VALUES ('PRINT_3D', 300000)
    ON CONFLICT (task_type) DO NOTHING;

-- New ledger type for a direct purchase (immediate debit, not a hold).
ALTER TABLE dtb_wallet_transactions DROP CONSTRAINT IF EXISTS chk_dtb_wallet_transactions_type;
ALTER TABLE dtb_wallet_transactions
    ADD CONSTRAINT chk_dtb_wallet_transactions_type
    CHECK (type IN ('TOPUP', 'HOLD', 'CAPTURE', 'RELEASE', 'REFUND', 'ADJUSTMENT', 'PURCHASE'));

-- Print orders.
CREATE TABLE dtb_print_orders (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID           NOT NULL,
    source_task_id UUID,
    amount         NUMERIC(15, 2) NOT NULL,
    currency       VARCHAR(3)     NOT NULL DEFAULT 'VND',
    status         VARCHAR(20)    NOT NULL DEFAULT 'PAID',
    note           VARCHAR(500),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dtb_print_orders_status
        CHECK (status IN ('PENDING', 'PAID', 'IN_PRODUCTION', 'SHIPPED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_dtb_print_orders_amount CHECK (amount > 0),
    CONSTRAINT fk_dtb_print_orders_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_dtb_print_orders_source_task_id
        FOREIGN KEY (source_task_id) REFERENCES dtb_meshy_tasks (id) ON DELETE SET NULL
);

CREATE INDEX idx_dtb_print_orders_user_created ON dtb_print_orders (user_id, created_at DESC);
