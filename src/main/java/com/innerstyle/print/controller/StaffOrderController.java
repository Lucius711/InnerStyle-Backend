package com.innerstyle.print.controller;

import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.meshy.dto.response.PrintabilityResponse;
import com.innerstyle.meshy.dto.response.RepairResponse;
import com.innerstyle.meshy.service.MeshyTaskService;
import com.innerstyle.print.dto.request.UpdateOrderStatusRequest;
import com.innerstyle.print.dto.response.StaffOrderResponse;
import com.innerstyle.print.service.StaffOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

/**
 * Staff order-fulfilment endpoints ({@code /api/staff/orders/**}). Staff browse
 * every customer's
 * 3D-print order, advance fulfilment status, and download the model file to
 * print.
 */
@Tag(name = "Staff Orders")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/staff/orders")
@PreAuthorize("hasRole('STAFF')")
@RequiredArgsConstructor
public class StaffOrderController {

    private final StaffOrderService staffOrderService;
    private final MeshyTaskService meshyTaskService;

    @Operation(summary = "List all customer print orders (optionally filtered by status)")
    @GetMapping
    public ApiResponse<Page<StaffOrderResponse>> list(
            @RequestParam(required = false) String status,
            @ParameterObject Pageable pageable) {
        return ApiResponse.success("staff.orders", staffOrderService.list(status, pageable));
    }

    @Operation(summary = "Get a single print order with full customer + shipping details")
    @GetMapping("/{id}")
    public ApiResponse<StaffOrderResponse> get(@PathVariable UUID id) {
        return ApiResponse.success("staff.order", staffOrderService.get(id));
    }

    @Operation(summary = "Advance the fulfilment status of a print order")
    @PatchMapping("/{id}/status")
    public ApiResponse<StaffOrderResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ApiResponse.success("staff.order.statusUpdated",
                staffOrderService.updateStatus(id, request.getStatus()));
    }

    @Operation(summary = "Proxy the order's model thumbnail — fetches a fresh signed URL from Meshy and streams the image bytes")
    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<byte[]> thumbnail(@PathVariable UUID id) {
        byte[] data = staffOrderService.fetchThumbnail(id);
        if (data == null || data.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(MediaType.IMAGE_PNG)
                .body(data);
    }

    @Operation(summary = "Analyse the 3D-print readiness of the order's model (watertight / holes / non-manifold)")
    @GetMapping("/{id}/printability")
    public ApiResponse<PrintabilityResponse> printability(@PathVariable UUID id) {
        return ApiResponse.success("staff.order.printability", staffOrderService.printability(id));
    }

    @Operation(summary = "Auto-repair the order's model into a watertight, printable mesh and save it "
            + "in place (the existing download then serves the repaired model). Returns before/after stats.")
    @PostMapping("/{id}/repair")
    public ApiResponse<RepairResponse> repair(@PathVariable UUID id) {
        return ApiResponse.success("staff.order.repaired", staffOrderService.repairModel(id));
    }

    @Operation(summary = "Download the customer's 3D model for this order as a ZIP (model + textures)")
    @GetMapping("/{id}/model")
    public ResponseEntity<StreamingResponseBody> downloadModel(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "glb") String format) {
        MeshyTaskService.ExportPrep prep = staffOrderService.prepareModelZip(id, format);
        StreamingResponseBody body = out -> meshyTaskService.writeZip(prep, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"order-" + id.toString().substring(0, 8) + ".zip\"")
                .body(body);
    }
}
