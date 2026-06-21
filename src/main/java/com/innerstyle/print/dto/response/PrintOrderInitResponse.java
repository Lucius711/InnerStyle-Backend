package com.innerstyle.print.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of placing a print order: the order id + the gateway URL to pay.
 */
@Schema(description = "Print order created, awaiting payment")
public record PrintOrderInitResponse(
    UUID orderId,
    BigDecimal amount,
    String payUrl
) {
}
