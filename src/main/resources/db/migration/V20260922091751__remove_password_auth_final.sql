-- =============================================================================
-- Sign-in is Google-only now -- remove username/password auth for good.
--
-- The seeded staff account (staff@innerstyle.local) is repurposed rather than
-- deleted when possible: it becomes dohuy2915@gmail.com so the SAME user row
-- (same id, same STAFF role link) auto-links on the next Google sign-in with
-- that email (see AuthServiceImpl#linkOrCreate -- it matches by email before
-- creating anything new).
--
-- But dohuy2915@gmail.com may already have its OWN dtb_users row (created by
-- linkOrCreate the first time that address signed in with Google, if that
-- happened before this migration ran) -- a blind rename then hits
-- idx_dtb_users_email_unique. Handle both orders: if that row already
-- exists, grant IT the STAFF role instead and retire the seed row.
-- (This replaces an earlier version of this migration that assumed the row
-- could never already exist; Postgres rolled that failed attempt back
-- entirely -- no partial state, nothing to repair.)
-- =============================================================================

DO $$
DECLARE
    staff_role_id    INTEGER;
    seed_user_id     UUID;
    existing_user_id UUID;
BEGIN
    SELECT id INTO staff_role_id FROM mtb_roles WHERE code = 'STAFF';
    SELECT id INTO seed_user_id FROM dtb_users WHERE LOWER(email) = 'staff@innerstyle.local';
    SELECT id INTO existing_user_id FROM dtb_users WHERE LOWER(email) = 'dohuy2915@gmail.com';

    IF existing_user_id IS NOT NULL THEN
        -- dohuy2915@gmail.com already has its own row -- grant it STAFF and
        -- drop the now-redundant seed row (user_roles/oauth_accounts/
        -- refresh_tokens cascade-delete with it; login_audit.user_id -> NULL).
        IF staff_role_id IS NOT NULL THEN
            INSERT INTO dtb_user_roles (user_id, role_id)
            VALUES (existing_user_id, staff_role_id)
            ON CONFLICT (user_id, role_id) DO NOTHING;
        END IF;

        IF seed_user_id IS NOT NULL AND seed_user_id <> existing_user_id THEN
            DELETE FROM dtb_users WHERE id = seed_user_id;
        END IF;
    ELSIF seed_user_id IS NOT NULL THEN
        -- No row for the real email yet -- repurpose the seed row as planned.
        UPDATE dtb_users SET email = 'dohuy2915@gmail.com' WHERE id = seed_user_id;
    END IF;
END $$;

UPDATE dtb_users SET username = NULL, password_hash = NULL
WHERE LOWER(email) = 'dohuy2915@gmail.com';

DROP INDEX IF EXISTS idx_dtb_users_username_unique;

ALTER TABLE dtb_users
    DROP COLUMN IF EXISTS username,
    DROP COLUMN IF EXISTS password_hash,
    DROP COLUMN IF EXISTS failed_login_count,
    DROP COLUMN IF EXISTS locked_until;
