-- =============================================================================
-- Membership + credit model (replaces the virtual wallet for 3D generation).
-- Each user has a membership (FREE/PRO/MAX) granting monthly credits; every 3D operation
-- consumes credits (Meshy-style). Pro/Max are bought via direct VNPay/MoMo payment.
-- =============================================================================

-- Master: subscription plans.
CREATE TABLE mtb_membership_plans (
    id              SERIAL PRIMARY KEY,
    code            VARCHAR(20)    NOT NULL UNIQUE,
    name            VARCHAR(100)   NOT NULL,
    monthly_credits INTEGER        NOT NULL,
    price           NUMERIC(15, 2) NOT NULL DEFAULT 0,
    currency        VARCHAR(3)     NOT NULL DEFAULT 'VND',
    is_active       BOOLEAN        NOT NULL DEFAULT TRUE,
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_mtb_membership_plans_code CHECK (code IN ('FREE', 'PRO', 'MAX')),
    CONSTRAINT chk_mtb_membership_plans_credits CHECK (monthly_credits >= 0),
    CONSTRAINT chk_mtb_membership_plans_price CHECK (price >= 0)
);

INSERT INTO mtb_membership_plans (code, name, monthly_credits, price, sort_order) VALUES
    ('FREE', 'Free', 20,   0,      0),
    ('PRO',  'Pro',  300,  99000,  1),
    ('MAX',  'Max',  1000, 299000, 2)
ON CONFLICT (code) DO NOTHING;

-- Master: credit cost per 3D operation.
CREATE TABLE mtb_operation_credits (
    id          SERIAL PRIMARY KEY,
    task_type   VARCHAR(32) NOT NULL UNIQUE,
    credit_cost INTEGER     NOT NULL,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_mtb_operation_credits_cost CHECK (credit_cost >= 0)
);

INSERT INTO mtb_operation_credits (task_type, credit_cost) VALUES
    ('IMAGE_TO_3D',        5),
    ('MULTI_IMAGE_TO_3D',  6),
    ('TEXT_TO_3D_PREVIEW', 3),
    ('TEXT_TO_3D_REFINE',  4),
    ('REMESH',             2),
    ('RETEXTURE',          3),
    ('RIG',                4),
    ('ANIMATE',            5),
    ('FIGURE_PROTOTYPE',   3),
    ('FIGURE_BUILD',       8)
ON CONFLICT (task_type) DO NOTHING;

-- One membership per user (current plan + remaining credits for the period).
CREATE TABLE dtb_user_memberships (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL UNIQUE,
    plan_id           INTEGER     NOT NULL,
    credits_remaining INTEGER     NOT NULL DEFAULT 0,
    period_start      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    period_end        TIMESTAMPTZ,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dtb_user_memberships_status CHECK (status IN ('ACTIVE', 'EXPIRED')),
    CONSTRAINT chk_dtb_user_memberships_credits CHECK (credits_remaining >= 0),
    CONSTRAINT fk_dtb_user_memberships_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_dtb_user_memberships_plan_id
        FOREIGN KEY (plan_id) REFERENCES mtb_membership_plans (id) ON DELETE RESTRICT
);

CREATE INDEX idx_dtb_user_memberships_plan_id ON dtb_user_memberships (plan_id);

-- Immutable credit ledger.
CREATE TABLE dtb_credit_transactions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL,
    type           VARCHAR(20) NOT NULL,
    amount         INTEGER     NOT NULL,
    balance_after  INTEGER     NOT NULL,
    reference_type VARCHAR(30),
    reference_id   UUID,
    description    VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dtb_credit_transactions_type
        CHECK (type IN ('GRANT', 'CONSUME', 'REFUND', 'ADJUSTMENT')),
    CONSTRAINT fk_dtb_credit_transactions_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE
);

CREATE INDEX idx_dtb_credit_transactions_user_created
    ON dtb_credit_transactions (user_id, created_at DESC);
