-- =============================================================================
-- Remove email/password authentication — sign-in is now social-only (Google/Facebook).
-- Drops the password hash column and the password-reset / email-verification token
-- tables. Social accounts already carry a NULL password_hash, so no data is lost for
-- them; any password-only account can no longer sign in (by design).
-- =============================================================================

ALTER TABLE dtb_users DROP COLUMN IF EXISTS password_hash;

DROP TABLE IF EXISTS dtb_email_verification_tokens;
DROP TABLE IF EXISTS dtb_password_reset_tokens;
