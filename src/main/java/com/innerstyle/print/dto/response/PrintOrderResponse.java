package com.innerstyle.print.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A 3D-print order (as seen by the customer who placed it)")
public record PrintOrderResponse(
    UUID id,
    UUID sourceTaskId,
    Integer sizeCm,
    BigDecimal amount,
    String currency,
    String status,
    String note,
    String recipientName,
    String recipientPhone,
    String provinceName,
    String wardName,
    String addressDetail,
    Instant createdAt
) {
}
