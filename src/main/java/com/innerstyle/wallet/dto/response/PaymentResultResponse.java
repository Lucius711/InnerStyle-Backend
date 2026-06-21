package com.innerstyle.wallet.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Result of confirming a gateway return (browser redirect). {@code credited} is true only when
 * this call is what actually topped up the wallet (idempotent: false if the IPN already did).
 */
@Schema(description = "Payment confirmation result")
public record PaymentResultResponse(
    String orderCode,
    String status,
    BigDecimal amount,
    boolean credited
) {
}
