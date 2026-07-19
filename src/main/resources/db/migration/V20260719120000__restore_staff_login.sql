-- =============================================================================
-- Restore login for the default STAFF account.
--
-- Why this is needed:
--   V20260625060746 seeded staff@innerstyle.local with a BCrypt password hash.
--   V20260708120000 then ran `DROP COLUMN password_hash`, destroying that hash.
--   V20260710120000 re-added the column as NULL — the seed never re-ran because
--   its `WHERE NOT EXISTS (email = ...)` guard still matched the surviving row.
--   V20260710130000 additionally made `username` the login identifier, and the
--   staff row has never had one. Result: the account cannot authenticate in ANY
--   environment (AuthServiceImpl#login resolves by username only, no email fallback).
--
-- This migration backfills both columns so the account works again.
--
--   username: staff
--   password: Staff@12345      <-- TEST CREDENTIAL, ROTATE BEFORE/AFTER PROD USE
--
-- Idempotent: UPDATEs the existing row, or INSERTs it if the DB was never seeded.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Existing staff row (the normal case): backfill username + password_hash.
-- ---------------------------------------------------------------------------
UPDATE dtb_users
SET username      = 'staff',
    password_hash = '$2a$10$.hx/oO0IJJBxQQFuRUSKvuuVO4mOYZFkblYD4B8BdakTiuiqMuSPC',
    status        = 'ACTIVE'
WHERE LOWER(email) = 'staff@innerstyle.local'
  AND (username IS NULL OR password_hash IS NULL);

-- ---------------------------------------------------------------------------
-- 2. Fallback: staff row absent entirely (fresh DB where the seed was skipped).
--    Guarded on BOTH email and username so it can never collide with the
--    partial unique index idx_dtb_users_username_unique.
-- ---------------------------------------------------------------------------
INSERT INTO dtb_users (username, email, password_hash, full_name, status,
                       email_verified, email_verified_at)
SELECT 'staff',
       'staff@innerstyle.local',
       '$2a$10$.hx/oO0IJJBxQQFuRUSKvuuVO4mOYZFkblYD4B8BdakTiuiqMuSPC',
       'InnerStyle Staff',
       'ACTIVE',
       TRUE,
       NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM dtb_users
    WHERE LOWER(email) = 'staff@innerstyle.local'
       OR LOWER(username) = 'staff'
);

-- ---------------------------------------------------------------------------
-- 3. Re-assert the STAFF role link (no-op if it already exists).
-- ---------------------------------------------------------------------------
INSERT INTO dtb_user_roles (user_id, role_id)
SELECT u.id, r.id
FROM dtb_users u
CROSS JOIN mtb_roles r
WHERE LOWER(u.username) = 'staff'
  AND r.code = 'STAFF'
ON CONFLICT (user_id, role_id) DO NOTHING;
