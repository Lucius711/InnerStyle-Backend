package com.innerstyle.meshy.controller;

import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.meshy.dto.response.R2BackfillResponse;
import com.innerstyle.meshy.service.MeshyR2BackfillJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff maintenance for 3D tasks ({@code /api/staff/3d/**}). */
@Tag(name = "Staff 3D")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/staff/3d")
@PreAuthorize("hasRole('STAFF')")
@RequiredArgsConstructor
public class StaffMeshyController {

    private final MeshyR2BackfillJob r2BackfillJob;

    /** Same job as the nightly cron, run now. Synchronous: a proxy timeout may cut the response
     *  on a big backlog, but the job keeps running and logs "R2 backfill done". */
    @Operation(summary = "Copy all still-available Meshy task files into R2 now (also runs nightly)")
    @PostMapping("/r2-backfill")
    public ApiResponse<R2BackfillResponse> r2Backfill() {
        return ApiResponse.success("meshy.r2Backfill.done", r2BackfillJob.run());
    }
}
