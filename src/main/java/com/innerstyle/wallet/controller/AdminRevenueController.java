package com.innerstyle.wallet.controller;

import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.wallet.dto.response.PaymentResultResponse;
import com.innerstyle.wallet.entity.enums.PaymentProvider;
import com.innerstyle.wallet.entity.enums.PaymentStatus;
import com.innerstyle.wallet.service.PaymentService;
import com.innerstyle.wallet.service.RevenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Admin revenue ({@code /api/admin/revenue/**}): payment orders from the DB, reconciled against payOS on demand. */
@Tag(name = "Admin Revenue")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/admin/revenue")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminRevenueController {

    private static final int MAX_PAGE_SIZE = 100;

    private final RevenueService revenueService;
    private final PaymentService paymentService;

    @Operation(summary = "Settled revenue totals (subscription + print) and payment counts")
    @GetMapping("/summary")
    public ApiResponse<RevenueService.Summary> summary() {
        return ApiResponse.success("revenue.summary", revenueService.summary());
    }

    @Operation(summary = "Payment orders, newest first, optionally filtered by status")
    @GetMapping("/payments")
    public ApiResponse<Page<RevenueService.PaymentRow>> payments(
        @RequestParam(required = false) PaymentStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return ApiResponse.success("revenue.payments", revenueService.list(status, pageable));
    }

    @Operation(summary = "Re-check one order at payOS and settle it if payOS says PAID (idempotent)")
    @PostMapping("/payments/{orderCode}/reconcile")
    public ApiResponse<PaymentResultResponse> reconcile(@PathVariable String orderCode) {
        return ApiResponse.success("revenue.reconciled",
            paymentService.confirmReturn(PaymentProvider.PAYOS, Map.of("orderCode", orderCode)));
    }
}
