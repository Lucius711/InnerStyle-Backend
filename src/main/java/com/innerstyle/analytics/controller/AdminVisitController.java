package com.innerstyle.analytics.controller;

import com.innerstyle.analytics.dto.response.DailyVisitResponse;
import com.innerstyle.analytics.service.VisitService;
import com.innerstyle.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin traffic stats ({@code /api/admin/visits/**}). */
@Tag(name = "Admin Visits")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/admin/visits")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminVisitController {

    private static final int MAX_DAYS = 90;

    private final VisitService visitService;

    @Operation(summary = "Visits per day for the last N days (1-90, default 14)")
    @GetMapping("/daily")
    public ApiResponse<List<DailyVisitResponse>> daily(@RequestParam(defaultValue = "14") int days) {
        return ApiResponse.success("visit.daily", visitService.dailyVisits(Math.clamp(days, 1, MAX_DAYS)));
    }

    @Operation(summary = "Number of ACTIVE accounts in dtb_users")
    @GetMapping("/active-accounts")
    public ApiResponse<Long> activeAccounts() {
        return ApiResponse.success("visit.activeAccounts", visitService.activeAccounts());
    }

    @Operation(summary = "Signed-in users online right now (heartbeat in the last 2 minutes)")
    @GetMapping("/online")
    public ApiResponse<Long> online() {
        return ApiResponse.success("visit.online", visitService.onlineUsers());
    }
}
