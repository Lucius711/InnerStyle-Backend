package com.innerstyle.notification.event;

import java.util.UUID;

/**
 * Staff posted a step on a print order's tracking timeline. Published inside the update
 * transaction; the email goes out only after it commits.
 */
public record PrintOrderProgressEvent(
    UUID orderId,
    String status,
    String note,
    String carrier,
    String trackingCode,
    String recipientEmail,
    String recipientName
) {
}
