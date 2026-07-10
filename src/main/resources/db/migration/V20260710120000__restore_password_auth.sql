-- =============================================================================
-- Restore email/password authentication (username = email + password).
-- Re-adds the password_hash column dropped by V20260708120000. Social accounts
-- keep a NULL password_hash (they still sign in via OAuth); local accounts store
-- a BCrypt hash. No email-verification / OTP flow is reintroduced.
-- =============================================================================

ALTER TABLE dtb_users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
