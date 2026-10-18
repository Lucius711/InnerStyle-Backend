package com.innerstyle.meshy.service;

import com.innerstyle.meshy.entity.enums.MeshyTaskStatus;
import com.innerstyle.meshy.entity.enums.MeshyTaskType;
import com.innerstyle.meshy.repository.MeshyTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Admin view of generated 3D models over {@code dtb_meshy_tasks}: totals per creation method and per account.
 * A "model" is a finished generation task (SUCCEEDED, or EXPIRED = succeeded then its Meshy URLs lapsed);
 * follow-up steps (refine/remesh/rig/animate) and user uploads are not counted as new models.
 * shortcut: concrete class with nested DTO records, same as RevenueService — split when it grows.
 * shortcut: the account list is unpaged and pivoted in memory — page it once accounts reach the thousands.
 */
@Service
@RequiredArgsConstructor
public class ModelStatsService {

    /** Task types that produce a brand-new model. FIGURE_BUILD = chibi figurine. */
    public static final Set<MeshyTaskType> CREATION_TYPES = Set.of(
        MeshyTaskType.IMAGE_TO_3D, MeshyTaskType.MULTI_IMAGE_TO_3D,
        MeshyTaskType.TEXT_TO_3D_PREVIEW, MeshyTaskType.FIGURE_BUILD);

    private static final Set<MeshyTaskStatus> CREATED_STATUSES = Set.of(MeshyTaskStatus.SUCCEEDED, MeshyTaskStatus.EXPIRED);

    private final MeshyTaskRepository meshyTaskRepository;

    public record Summary(long totalModels, Map<MeshyTaskType, Long> byType) {
    }

    public record AccountRow(UUID userId, String email, String fullName, long total,
                             Map<MeshyTaskType, Long> byType, Instant lastCreatedAt) {
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        Map<MeshyTaskType, Long> byType = emptyCounts();
        meshyTaskRepository.countByType(CREATION_TYPES, CREATED_STATUSES)
            .forEach(r -> byType.put(r.getTaskType(), r.getTotal()));
        return new Summary(byType.values().stream().mapToLong(Long::longValue).sum(), byType);
    }

    /** Accounts that created at least one model, most models first. Legacy rows without an owner are skipped. */
    @Transactional(readOnly = true)
    public List<AccountRow> accounts() {
        Map<UUID, AccountRow> rows = new LinkedHashMap<>();
        for (MeshyTaskRepository.AccountTypeCount r : meshyTaskRepository.countByAccountAndType(CREATION_TYPES, CREATED_STATUSES)) {
            AccountRow prev = rows.get(r.getUserId());
            Map<MeshyTaskType, Long> byType = prev == null ? emptyCounts() : prev.byType();
            byType.put(r.getTaskType(), r.getTotal());
            Instant last = prev == null || r.getLastCreatedAt().isAfter(prev.lastCreatedAt())
                ? r.getLastCreatedAt() : prev.lastCreatedAt();
            rows.put(r.getUserId(), new AccountRow(r.getUserId(), r.getEmail(), r.getFullName(),
                (prev == null ? 0 : prev.total()) + r.getTotal(), byType, last));
        }
        return rows.values().stream()
            .sorted(Comparator.comparingLong(AccountRow::total).reversed())
            .toList();
    }

    private static Map<MeshyTaskType, Long> emptyCounts() {
        Map<MeshyTaskType, Long> m = new EnumMap<>(MeshyTaskType.class);
        CREATION_TYPES.forEach(t -> m.put(t, 0L));
        return m;
    }
}
