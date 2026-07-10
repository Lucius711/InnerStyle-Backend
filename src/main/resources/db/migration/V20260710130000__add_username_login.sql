-- =============================================================================
-- Username + password authentication.
-- Adds a `username` column used as the local login identifier and makes `email`
-- optional (social accounts still carry an email; username-only accounts do not).
-- Partial unique index allows many NULL usernames (social accounts have none).
-- =============================================================================

ALTER TABLE dtb_users ADD COLUMN IF NOT EXISTS username VARCHAR(50);

ALTER TABLE dtb_users ALTER COLUMN email DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_dtb_users_username_unique
    ON dtb_users (LOWER(username)) WHERE username IS NOT NULL;
