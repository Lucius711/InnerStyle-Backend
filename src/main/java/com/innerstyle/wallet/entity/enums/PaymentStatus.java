package com.innerstyle.wallet.entity.enums;

/**
 * Top-up order lifecycle. Mirrors {@code chk_dtb_payment_orders_status}.
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    EXPIRED,
    CANCELED
}
