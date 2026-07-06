-- =============================================================================
-- Email verification moves from a magic-link token to a short numeric OTP.
-- The OTP is short (default 6 digits) and therefore brute-forceable, so we
-- track verification attempts per token and lock the token once the configured
-- maximum is exceeded (enforced in AuthServiceImpl against app.auth.otp-max-attempts).
-- token_hash still stores the SHA-256 hex digest of the OTP (never the OTP itself).
-- =============================================================================

ALTER TABLE dtb_email_verification_tokens
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
