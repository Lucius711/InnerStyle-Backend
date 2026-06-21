package com.innerstyle.print.entity.enums;

/**
 * Lifecycle of a 3D-print order. Mirrors {@code chk_dtb_print_orders_status}.
 * Paid on creation; production/shipping states are advanced by an operator/admin.
 */
public enum PrintOrderStatus {
    PENDING,
    PAID,
    IN_PRODUCTION,
    SHIPPED,
    COMPLETED,
    CANCELLED
}
