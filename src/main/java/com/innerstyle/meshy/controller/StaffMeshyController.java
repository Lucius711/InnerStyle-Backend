package com.innerstyle.meshy.controller;

import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.meshy.dto.response.R2BackfillResponse;
import com.innerstyle.meshy.service.MeshyTaskService;
import com.innerstyle.meshy.service.MeshyTaskService.R2BackfillResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Staff maintenance for 3D tasks ({@code /api/staff/3d/**}). */
@Tag(name = "Staff 3D")
@SecurityRequirement(name = "bearer-jwt")
@Slf4j
@RestController
@RequestMapping("/staff/3d")
@PreAuthorize("hasRole('STAFF')")
@RequiredArgsConstructor
public class StaffMeshyController {

    private final MeshyTaskService meshyTaskService;

    /**
     * Copy every still-available Meshy task (model + PBR maps) into R2. Each task runs in its own
     * transaction (one service call per task), so one failure never rolls back the rest. Safe to
     * re-run: files already in R2 are skipped.
     * ponytail: synchronous; a reverse-proxy timeout may cut the HTTP response on a large backlog
     * but the loop keeps running and logs the totals — make it a background job if that matters.
     */
    @Operation(summary = "Copy all still-available Meshy task files (model + textures) into R2")
    @PostMapping("/r2-backfill")
    public ApiResponse<R2BackfillResponse> r2Backfill() {
        List<UUID> ids = meshyTaskService.r2BackfillCandidates();
        Map<R2BackfillResult, Integer> counts = new EnumMap<>(R2BackfillResult.class);
        for (UUID id : ids) {
            R2BackfillResult result;
            try {
                result = meshyTaskService.backfillToR2(id);
            } catch (RuntimeException e) {
                log.warn("R2 backfill failed for task {}: {}", id, e.getMessage());
                result = R2BackfillResult.FAILED;
            }
            counts.merge(result, 1, Integer::sum);
        }
        R2BackfillResponse response = new R2BackfillResponse(ids.size(),
            counts.getOrDefault(R2BackfillResult.COPIED, 0),
            counts.getOrDefault(R2BackfillResult.PURGED, 0),
            counts.getOrDefault(R2BackfillResult.FAILED, 0));
        log.info("R2 backfill done: {}", response);
        return ApiResponse.success("meshy.r2Backfill.done", response);
    }
}
