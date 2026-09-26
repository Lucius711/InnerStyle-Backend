package com.innerstyle.meshy.service;

import com.innerstyle.meshy.dto.response.R2BackfillResponse;
import com.innerstyle.meshy.service.MeshyTaskService.R2BackfillResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Copies every still-available Meshy task (model + PBR maps) into R2, and hides tasks Meshy has
 * purged. Runs nightly and on demand (POST /api/staff/3d/r2-backfill). Each task is one service
 * call = its own transaction, so one failure never rolls back the rest. Safe to re-run: files
 * already in R2 are skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeshyR2BackfillJob {

    private final MeshyTaskService meshyTaskService;

    @Scheduled(cron = "${app.meshy.r2-backfill-cron:0 0 0 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void nightly() {
        run();
    }

    // ponytail: synchronized = no overlap between cron and a manual run on this instance;
    // add a DB/Redis lock if the backend is ever scaled to several instances.
    public synchronized R2BackfillResponse run() {
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
        return response;
    }
}
