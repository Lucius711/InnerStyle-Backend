package com.innerstyle.meshy.controller;

import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.meshy.service.ModelStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin model stats ({@code /api/admin/models/**}): how many 3D models were created, by method and by account. */
@Tag(name = "Admin Model Stats")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/admin/models")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminModelStatsController {

    private final ModelStatsService modelStatsService;

    @Operation(summary = "Total created models and the count per creation method")
    @GetMapping("/summary")
    public ApiResponse<ModelStatsService.Summary> summary() {
        return ApiResponse.success("modelStats.summary", modelStatsService.summary());
    }

    @Operation(summary = "Accounts that created models, most models first, with a per-method breakdown")
    @GetMapping("/accounts")
    public ApiResponse<List<ModelStatsService.AccountRow>> accounts() {
        return ApiResponse.success("modelStats.accounts", modelStatsService.accounts());
    }
}
