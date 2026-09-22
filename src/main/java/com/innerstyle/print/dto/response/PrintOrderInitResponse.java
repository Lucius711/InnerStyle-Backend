package com.innerstyle.print.dto.response;

import com.innerstyle.wallet.dto.response.PaymentInitResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Result of placing a print order: the order id plus the payment init (gateway URL and, payOS
 * only, the raw VietQR payload for an in-app QR).
 */
@Schema(description = "Print order created, awaiting payment")
public record PrintOrderInitResponse(
    UUID orderId,
    PaymentInitResponse payment
) {
}
