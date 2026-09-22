package com.innerstyle.print.service;

import com.innerstyle.meshy.dto.response.PrintabilityResponse;
import com.innerstyle.meshy.dto.response.RepairResponse;
import com.innerstyle.meshy.service.MeshyTaskService;
import com.innerstyle.print.dto.response.StaffOrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Staff-facing operations on customer 3D-print orders: browse all orders (with customer +
 * shipping details), inspect one, advance its fulfilment status, and download the model file.
 */
public interface StaffOrderService {

    Page<StaffOrderResponse> list(String status, Pageable pageable);

    StaffOrderResponse get(UUID orderId);

    StaffOrderResponse updateStatus(UUID orderId, String status);

    /** Resolve the order's source model and prepare it for streamed zip download. */
    MeshyTaskService.ExportPrep prepareModelZip(UUID orderId, String format);

    /** Analyse the 3D-print readiness of the order's source model (watertight / holes / etc.). */
    PrintabilityResponse printability(UUID orderId);

    /**
     * Auto-repair the order's source model into a watertight, printable mesh and save it in place,
     * so the staff download then serves the repaired model. Returns the before/after reports.
     */
    RepairResponse repairModel(UUID orderId);

    /**
     * Revert the order's source model back to its pre-repair backup, if one exists. Returns the
     * before/after printability reports.
     */
    RepairResponse revertModel(UUID orderId);

    /**
     * Fetch a fresh thumbnail image for the order's model, proxied through the server so
     * the signed Meshy CDN URL is never exposed to the browser (and always up-to-date).
     * Returns {@code null} if the order has no source task or no thumbnail.
     */
    byte[] fetchThumbnail(UUID orderId);
}
