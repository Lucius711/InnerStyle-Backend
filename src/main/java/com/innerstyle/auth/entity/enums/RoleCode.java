package com.innerstyle.auth.entity.enums;

/**
 * System roles seeded in {@code mtb_roles}. The Spring Security authority is
 * {@code ROLE_<name>} (e.g. {@code ROLE_USER}).
 */
public enum RoleCode {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
