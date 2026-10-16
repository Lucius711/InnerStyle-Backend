package com.innerstyle.print.service;

import com.innerstyle.print.dto.response.PrintOrderTrackingResponse;
import com.innerstyle.print.entity.PrintOrder;
import com.innerstyle.print.entity.enums.PrintOrderStatus;

import java.util.UUID;

/** Writes and reads the tracking timeline of print orders. */
public interface PrintOrderEventService {

    /** Append one step to the order's timeline. {@code note} and {@code actorId} may be null. */
    void record(UUID printOrderId, PrintOrderStatus status, String note, UUID actorId);

    /** The order's tracking view (no ownership check: callers do that). */
    PrintOrderTrackingResponse tracking(PrintOrder order);
}
