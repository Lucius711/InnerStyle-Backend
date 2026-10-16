package com.innerstyle.print.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Tracking view of a print order: current status, carrier, and the timeline of updates (oldest first)")
public record PrintOrderTrackingResponse(
    UUID id,
    String status,
    String carrier,
    String trackingCode,
    List<Event> events
) {
    /** {@code note} is staff free text, a {@code system.*} key, or null. */
    public record Event(String status, String note, Instant at) {
    }
}
