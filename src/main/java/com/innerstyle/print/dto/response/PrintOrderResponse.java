package com.innerstyle.print.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "A 3D-print order")
public record PrintOrderResponse(
    UUID id,
    UUID sourceTaskId,
    BigDecimal amount,
    String currency,
    String status,
    String note,
    Instant createdAt
) {
}
