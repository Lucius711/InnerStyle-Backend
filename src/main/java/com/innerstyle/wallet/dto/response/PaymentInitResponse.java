package com.innerstyle.wallet.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Result of starting a direct payment: the gateway URL the client opens to pay.
 */
@Schema(description = "Payment init with the gateway URL")
public record PaymentInitResponse(
    String orderCode,
    String provider,
    BigDecimal amount,
    String payUrl
) {
}
