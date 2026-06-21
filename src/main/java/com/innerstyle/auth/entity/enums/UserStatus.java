package com.innerstyle.auth.entity.enums;

/**
 * Account lifecycle state. Persisted as a string and mirrored by the
 * {@code chk_dtb_users_status} CHECK constraint.
 */
public enum UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    BANNED
}
