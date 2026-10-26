package com.innerstyle.wallet.controller;

import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.wallet.service.PlanStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin plan stats ({@code /api/admin/plans/**}): paid membership plans, counted per plan type. */
@Tag(name = "Admin Plan Stats")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/admin/plans")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPlanStatsController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PlanStatsService planStatsService;

    @Operation(summary = "Paid plan purchases: totals and count/revenue per plan")
    @GetMapping("/summary")
    public ApiResponse<PlanStatsService.Summary> summary() {
        return ApiResponse.success("planStats.summary", planStatsService.summary());
    }

    @Operation(summary = "Paid plan purchases, newest first, optionally filtered by plan code")
    @GetMapping("/purchases")
    public ApiResponse<Page<PlanStatsService.PurchaseRow>> purchases(
        @RequestParam(required = false) String plan,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return ApiResponse.success("planStats.purchases", planStatsService.purchases(plan, pageable));
    }
}
