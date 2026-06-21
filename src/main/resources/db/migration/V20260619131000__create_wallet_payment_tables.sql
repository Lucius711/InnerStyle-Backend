-- =============================================================================
-- Wallet & Payment module  (authorization-hold / "cọc tiền" model)
--
-- Flow:
--   1. User tops up the wallet via VNPay / MoMo      -> dtb_payment_orders
--   2. Gateway IPN confirms                          -> dtb_payment_callbacks
--      => wallet.available_balance += amount, ledger TOPUP row
--   3. Starting a 3D job places a HOLD on funds      -> dtb_holds (status HELD)
--      => available_balance -= price, held_balance += price, ledger HOLD row
--   4a. Job SUCCEEDED  -> CAPTURE the hold           => held_balance -= price,
--       ledger CAPTURE row (money consumed)
--   4b. Job FAILED     -> RELEASE the hold           => held_balance -= price,
--       available_balance += price, ledger RELEASE row (refunded to wallet)
--
-- All money columns are NUMERIC(15,2) in a single wallet currency (default VND).
-- Balances are guarded by CHECK (>= 0); concurrency by an optimistic `version`.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- Master: unit price per 3D operation (drives how much to HOLD).
-- ----------------------------------------------------------------------------
CREATE TABLE mtb_pricing (
    id         SERIAL PRIMARY KEY,
    task_type  VARCHAR(32)    NOT NULL UNIQUE,
    unit_price NUMERIC(15, 2) NOT NULL,
    currency   CHAR(3)        NOT NULL DEFAULT 'VND',
    is_active  BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_mtb_pricing_task_type
        CHECK (task_type IN ('IMAGE_TO_3D', 'TEXT_TO_3D_PREVIEW', 'TEXT_TO_3D_REFINE',
                             'REMESH', 'RETEXTURE', 'RIG', 'ANIMATE')),
    CONSTRAINT chk_mtb_pricing_unit_price CHECK (unit_price >= 0)
);

INSERT INTO mtb_pricing (task_type, unit_price) VALUES
    ('IMAGE_TO_3D',        20000),
    ('TEXT_TO_3D_PREVIEW', 10000),
    ('TEXT_TO_3D_REFINE',  15000),
    ('REMESH',              8000),
    ('RETEXTURE',          12000),
    ('RIG',                15000),
    ('ANIMATE',            18000)
ON CONFLICT (task_type) DO NOTHING;

-- ----------------------------------------------------------------------------
-- Wallet: one per user. available_balance is spendable; held_balance is the
-- sum of active authorization holds. `version` = JPA @Version optimistic lock.
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_wallets (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID           NOT NULL UNIQUE,
    currency          CHAR(3)        NOT NULL DEFAULT 'VND',
    available_balance NUMERIC(15, 2) NOT NULL DEFAULT 0,
    held_balance      NUMERIC(15, 2) NOT NULL DEFAULT 0,
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_dtb_wallets_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE,
    CONSTRAINT chk_dtb_wallets_available_nonneg CHECK (available_balance >= 0),
    CONSTRAINT chk_dtb_wallets_held_nonneg      CHECK (held_balance >= 0)
);

-- ----------------------------------------------------------------------------
-- Top-up orders through a payment gateway (VNPay / MoMo).
-- order_code is our own idempotent reference shown to the gateway.
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_payment_orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_code      VARCHAR(40)    NOT NULL UNIQUE,
    user_id         UUID           NOT NULL,
    wallet_id       UUID           NOT NULL,
    provider        VARCHAR(20)    NOT NULL,
    amount          NUMERIC(15, 2) NOT NULL,
    currency        CHAR(3)        NOT NULL DEFAULT 'VND',
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    provider_txn_ref VARCHAR(100),
    bank_code       VARCHAR(40),
    return_url      TEXT,
    description     VARCHAR(255),
    paid_at         TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dtb_payment_orders_provider
        CHECK (provider IN ('VNPAY', 'MOMO')),
    CONSTRAINT chk_dtb_payment_orders_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELED')),
    CONSTRAINT chk_dtb_payment_orders_amount CHECK (amount > 0),
    CONSTRAINT fk_dtb_payment_orders_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_dtb_payment_orders_wallet_id
        FOREIGN KEY (wallet_id) REFERENCES dtb_wallets (id) ON DELETE CASCADE
);

CREATE INDEX idx_dtb_payment_orders_user_id ON dtb_payment_orders (user_id);
CREATE INDEX idx_dtb_payment_orders_status ON dtb_payment_orders (status);
CREATE INDEX idx_dtb_payment_orders_provider_ref
    ON dtb_payment_orders (provider, provider_txn_ref);
CREATE INDEX idx_dtb_payment_orders_created_at ON dtb_payment_orders (created_at DESC);

-- ----------------------------------------------------------------------------
-- Raw gateway IPN / return callbacks (audit + idempotency / replay defence).
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_payment_callbacks (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_order_id UUID,
    provider         VARCHAR(20) NOT NULL,
    kind             VARCHAR(10) NOT NULL DEFAULT 'IPN',
    raw_payload      JSONB       NOT NULL,
    signature_valid  BOOLEAN     NOT NULL DEFAULT FALSE,
    response_code    VARCHAR(20),
    received_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dtb_payment_callbacks_provider
        CHECK (provider IN ('VNPAY', 'MOMO')),
    CONSTRAINT chk_dtb_payment_callbacks_kind
        CHECK (kind IN ('IPN', 'RETURN')),
    CONSTRAINT fk_dtb_payment_callbacks_order_id
        FOREIGN KEY (payment_order_id) REFERENCES dtb_payment_orders (id) ON DELETE SET NULL
);

CREATE INDEX idx_dtb_payment_callbacks_order_id ON dtb_payment_callbacks (payment_order_id);
CREATE INDEX idx_dtb_payment_callbacks_received_at ON dtb_payment_callbacks (received_at DESC);

-- ----------------------------------------------------------------------------
-- Authorization holds ("cọc tiền"). One hold backs one 3D job (FK added later
-- in the meshy-link migration). status lifecycle: HELD -> CAPTURED | RELEASED | EXPIRED.
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_holds (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id       UUID           NOT NULL,
    user_id         UUID           NOT NULL,
    amount          NUMERIC(15, 2) NOT NULL,
    captured_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    status          VARCHAR(20)    NOT NULL DEFAULT 'HELD',
    reason          VARCHAR(255),
    expires_at      TIMESTAMPTZ,
    settled_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dtb_holds_status
        CHECK (status IN ('HELD', 'CAPTURED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT chk_dtb_holds_amount CHECK (amount > 0),
    CONSTRAINT chk_dtb_holds_captured CHECK (captured_amount >= 0 AND captured_amount <= amount),
    CONSTRAINT fk_dtb_holds_wallet_id
        FOREIGN KEY (wallet_id) REFERENCES dtb_wallets (id) ON DELETE CASCADE,
    CONSTRAINT fk_dtb_holds_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE
);

CREATE INDEX idx_dtb_holds_wallet_id ON dtb_holds (wallet_id);
CREATE INDEX idx_dtb_holds_user_id ON dtb_holds (user_id);
CREATE INDEX idx_dtb_holds_status ON dtb_holds (status);
-- Find holds that should auto-expire and be released.
CREATE INDEX idx_dtb_holds_active_expiry
    ON dtb_holds (expires_at) WHERE status = 'HELD';

-- ----------------------------------------------------------------------------
-- Immutable wallet ledger. Every balance change appends exactly one row.
-- reference_type/id loosely links to the cause (payment order, hold, task).
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_wallet_transactions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id        UUID           NOT NULL,
    type             VARCHAR(20)    NOT NULL,
    amount           NUMERIC(15, 2) NOT NULL,
    available_after  NUMERIC(15, 2) NOT NULL,
    held_after       NUMERIC(15, 2) NOT NULL,
    reference_type   VARCHAR(30),
    reference_id     UUID,
    description      VARCHAR(255),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dtb_wallet_transactions_type
        CHECK (type IN ('TOPUP', 'HOLD', 'CAPTURE', 'RELEASE', 'REFUND', 'ADJUSTMENT')),
    CONSTRAINT chk_dtb_wallet_transactions_amount CHECK (amount > 0),
    CONSTRAINT fk_dtb_wallet_transactions_wallet_id
        FOREIGN KEY (wallet_id) REFERENCES dtb_wallets (id) ON DELETE CASCADE
);

CREATE INDEX idx_dtb_wallet_transactions_wallet_created
    ON dtb_wallet_transactions (wallet_id, created_at DESC);
CREATE INDEX idx_dtb_wallet_transactions_reference
    ON dtb_wallet_transactions (reference_type, reference_id);
