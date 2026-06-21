-- =============================================================================
-- Auth & Identity module
-- Roles (master) + users + role assignment + social login + token tables.
-- Full auth: email/password, JWT refresh rotation, social login, email verify,
-- password reset, login audit. Conforms to rules 11 (multi-role) & 14 (schema).
-- =============================================================================

-- ----------------------------------------------------------------------------
-- Master: system roles. USER / ADMIN.
-- Referenced by NOT NULL FKs -> seed here (allowed: tiny, static, idempotent).
-- ----------------------------------------------------------------------------
CREATE TABLE mtb_roles (
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO mtb_roles (code, name, description) VALUES
    ('USER',  'End User',      'Standard customer who generates 3D models'),
    ('ADMIN', 'Administrator', 'Full system administrator')
ON CONFLICT (code) DO NOTHING;

-- ----------------------------------------------------------------------------
-- Users. password_hash is NULL-able to support social-only accounts.
-- avatar_url stores a RELATIVE path (rule 16-file-url-storage).
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email             VARCHAR(255) NOT NULL,
    password_hash     VARCHAR(255),
    full_name         VARCHAR(255) NOT NULL,
    phone_number      VARCHAR(20),
    avatar_url        TEXT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified_at TIMESTAMPTZ,
    last_login_at     TIMESTAMPTZ,
    failed_login_count INTEGER     NOT NULL DEFAULT 0,
    locked_until      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dtb_users_status
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'BANNED'))
);

-- Case-insensitive unique email (citext-free approach via lower()).
CREATE UNIQUE INDEX idx_dtb_users_email_unique ON dtb_users (LOWER(email));
CREATE INDEX idx_dtb_users_status ON dtb_users (status);
CREATE UNIQUE INDEX idx_dtb_users_phone_unique
    ON dtb_users (phone_number) WHERE phone_number IS NOT NULL;

-- ----------------------------------------------------------------------------
-- User <-> Role (many-to-many). Master FK -> RESTRICT; regular FK -> CASCADE.
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_user_roles (
    user_id    UUID    NOT NULL,
    role_id    INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dtb_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_dtb_user_roles_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_dtb_user_roles_role_id
        FOREIGN KEY (role_id) REFERENCES mtb_roles (id) ON DELETE RESTRICT
);

CREATE INDEX idx_dtb_user_roles_role_id ON dtb_user_roles (role_id);

-- ----------------------------------------------------------------------------
-- Social / OAuth linked accounts (Google, Facebook).
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_oauth_accounts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email            VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_dtb_oauth_accounts_provider
        CHECK (provider IN ('GOOGLE', 'FACEBOOK')),
    CONSTRAINT fk_dtb_oauth_accounts_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE,
    CONSTRAINT uq_dtb_oauth_accounts_provider_user
        UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_dtb_oauth_accounts_user_id ON dtb_oauth_accounts (user_id);

-- ----------------------------------------------------------------------------
-- Refresh tokens (rotation + revocation). Only the SHA-256 hash is stored.
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID,
    user_agent  VARCHAR(255),
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_dtb_refresh_tokens_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE,
    CONSTRAINT fk_dtb_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by) REFERENCES dtb_refresh_tokens (id) ON DELETE SET NULL
);

CREATE INDEX idx_dtb_refresh_tokens_user_id ON dtb_refresh_tokens (user_id);
CREATE INDEX idx_dtb_refresh_tokens_expires_at ON dtb_refresh_tokens (expires_at);

-- ----------------------------------------------------------------------------
-- Email verification tokens (single active per send; hashed).
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_email_verification_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_dtb_email_verification_tokens_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE
);

CREATE INDEX idx_dtb_email_verification_tokens_user_id
    ON dtb_email_verification_tokens (user_id);

-- ----------------------------------------------------------------------------
-- Password reset tokens (hashed, short TTL enforced in app + expires_at).
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_dtb_password_reset_tokens_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE CASCADE
);

CREATE INDEX idx_dtb_password_reset_tokens_user_id
    ON dtb_password_reset_tokens (user_id);

-- ----------------------------------------------------------------------------
-- Login audit (brute-force forensics). user_id SET NULL: keep the audit row.
-- ----------------------------------------------------------------------------
CREATE TABLE dtb_login_audit (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID,
    email_attempted VARCHAR(255) NOT NULL,
    success         BOOLEAN      NOT NULL,
    failure_reason  VARCHAR(100),
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_dtb_login_audit_user_id
        FOREIGN KEY (user_id) REFERENCES dtb_users (id) ON DELETE SET NULL
);

CREATE INDEX idx_dtb_login_audit_email ON dtb_login_audit (LOWER(email_attempted));
CREATE INDEX idx_dtb_login_audit_created_at ON dtb_login_audit (created_at DESC);
