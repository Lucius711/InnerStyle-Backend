package com.innerstyle.print.service.impl;

import com.innerstyle.auth.entity.User;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.ResourceNotFoundException;
import com.innerstyle.common.exception.UpstreamServiceException;
import com.innerstyle.meshy.client.MeshyClient;
import com.innerstyle.meshy.client.dto.MeshyTaskDto;
import com.innerstyle.meshy.dto.response.PrintabilityResponse;
import com.innerstyle.meshy.dto.response.RepairResponse;
import com.innerstyle.meshy.entity.MeshyTask;
import com.innerstyle.meshy.repository.MeshyTaskRepository;
import com.innerstyle.meshy.service.MeshyTaskService;
import com.innerstyle.meshy.service.PrintabilityService;
import com.innerstyle.print.dto.response.StaffOrderResponse;
import com.innerstyle.print.entity.PrintOrder;
import com.innerstyle.print.entity.enums.PrintOrderStatus;
import com.innerstyle.print.repository.PrintOrderRepository;
import com.innerstyle.print.service.StaffOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Default {@link StaffOrderService}. Reads orders across all customers, joins the source Meshy
 * task to expose downloadable formats + thumbnail, and proxies model bytes for download.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffOrderServiceImpl implements StaffOrderService {

    private final PrintOrderRepository printOrderRepository;
    private final MeshyTaskRepository meshyTaskRepository;
    private final MeshyTaskService meshyTaskService;
    private final PrintabilityService printabilityService;
    private final MeshyClient meshyClient;

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Override
    @Transactional(readOnly = true)
    public Page<StaffOrderResponse> list(String status, Pageable pageable) {
        Page<PrintOrder> page;
        if (status == null || status.isBlank()) {
            page = printOrderRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            PrintOrderStatus parsed = parseStatus(status);
            page = printOrderRepository.findByStatusOrderByCreatedAtDesc(parsed, pageable);
        }
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public StaffOrderResponse get(UUID orderId) {
        return toResponse(getOrderOrThrow(orderId));
    }

    @Override
    @Transactional
    public StaffOrderResponse updateStatus(UUID orderId, String status) {
        PrintOrder order = getOrderOrThrow(orderId);
        order.setStatus(parseStatus(status));
        printOrderRepository.save(order);
        log.info("Staff updated print order {} -> {}", orderId, status);
        return toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public MeshyTaskService.ExportPrep prepareModelZip(UUID orderId, String format) {
        PrintOrder order = getOrderOrThrow(orderId);
        if (order.getSourceTaskId() == null) {
            throw new BadRequestException("staff.order.noModel");
        }
        return meshyTaskService.prepareTask(order.getSourceTaskId(), format);
    }

    @Override
    @Transactional(readOnly = true)
    public PrintabilityResponse printability(UUID orderId) {
        return printabilityService.analyze(sourceTaskIdOrThrow(orderId));
    }

    @Override
    @Transactional
    public RepairResponse repairModel(UUID orderId) {
        // Owner check is unnecessary here — the controller is @PreAuthorize("hasRole('STAFF')").
        return meshyTaskService.repairInPlace(sourceTaskIdOrThrow(orderId));
    }

    @Override
    @Transactional
    public RepairResponse revertModel(UUID orderId) {
        return meshyTaskService.revertToOriginal(sourceTaskIdOrThrow(orderId));
    }

    /** The order's source model task id, or a 400 if the order has no model attached. */
    private UUID sourceTaskIdOrThrow(UUID orderId) {
        PrintOrder order = getOrderOrThrow(orderId);
        if (order.getSourceTaskId() == null) {
            throw new BadRequestException("staff.order.noModel");
        }
        return order.getSourceTaskId();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] fetchThumbnail(UUID orderId) {
        PrintOrder order = getOrderOrThrow(orderId);
        if (order.getSourceTaskId() == null) return null;

        MeshyTask task = meshyTaskRepository.findById(order.getSourceTaskId()).orElse(null);
        if (task == null || task.getMeshyTaskId() == null) return null;

        // Always fetch a fresh signed URL from Meshy API — stored URLs expire.
        String freshUrl;
        try {
            MeshyTaskDto dto = meshyClient.getTask(task.getTaskType(), task.getMeshyTaskId());
            freshUrl = dto == null ? null : dto.getThumbnailUrl();
        } catch (Exception e) {
            log.warn("Failed to get fresh thumbnail URL for task {}: {}", task.getId(), e.getMessage());
            // Fall back to the stored URL (may be expired).
            freshUrl = task.getThumbnailUrl();
        }
        if (freshUrl == null || freshUrl.isBlank()) return null;

        try {
            HttpResponse<byte[]> resp = HTTP.send(
                HttpRequest.newBuilder(URI.create(freshUrl))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Meshy thumbnail returned HTTP {} for order {}", resp.statusCode(), orderId);
                return null;
            }
            return resp.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Failed to fetch thumbnail for order {}: {}", orderId, e.getMessage());
            return null;
        }
    }

    // ----- helpers -----

    private PrintOrder getOrderOrThrow(UUID orderId) {
        return printOrderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("print.order.notFound"));
    }

    private PrintOrderStatus parseStatus(String status) {
        try {
            return PrintOrderStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("2.status.invalid");
        }
    }

    private StaffOrderResponse toResponse(PrintOrder o) {
        User customer = o.getUser();
        MeshyTask task = o.getSourceTaskId() == null ? null
            : meshyTaskRepository.findById(o.getSourceTaskId()).orElse(null);
        String thumbnail = task == null ? null : task.getThumbnailUrl();
        List<String> formats = task == null || task.getModelUrls() == null
            ? List.of()
            : task.getModelUrls().keySet().stream().sorted().toList();

        return new StaffOrderResponse(
            o.getId(),
            o.getStatus().name(),
            o.getSizeCm(),
            o.getAmount(),
            o.getCurrency(),
            o.getCreatedAt(),
            o.getUpdatedAt(),
            customer == null ? null : customer.getId(),
            customer == null ? null : customer.getEmail(),
            customer == null ? null : customer.getFullName(),
            o.getRecipientName(),
            o.getRecipientEmail(),
            o.getRecipientPhone(),
            o.getProvinceName(),
            o.getWardName(),
            o.getAddressDetail(),
            o.getLatitude(),
            o.getLongitude(),
            o.getNote(),
            o.getSourceTaskId(),
            thumbnail,
            formats);
    }
}
