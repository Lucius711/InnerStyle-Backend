package com.innerstyle.print.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A 3D-print order as seen by staff: full customer + shipping details plus the model
 * (source task) needed to download and fulfil the order.
 */
@Schema(description = "A customer 3D-print order with full fulfilment details (staff view)")
public record StaffOrderResponse(
    UUID id,
    String status,
    Integer sizeCm,
    BigDecimal amount,
    String currency,
    Instant createdAt,
    Instant updatedAt,

    // Account that placed the order
    UUID customerId,
    String customerEmail,
    String customerName,

    // Recipient
    String recipientName,
    String recipientEmail,
    String recipientPhone,

    // Shipping address
    String provinceName,
    String wardName,
    String addressDetail,
    BigDecimal latitude,
    BigDecimal longitude,
    String note,

    // 3D model to print
    UUID sourceTaskId,
    String modelThumbnailUrl,
    List<String> availableFormats
) {
}
