package com.innerstyle.meshy.repository;

import com.innerstyle.meshy.entity.MeshyTask;
import com.innerstyle.meshy.entity.enums.MeshyTaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeshyTaskRepository extends JpaRepository<MeshyTask, UUID> {

    Optional<MeshyTask> findByMeshyTaskId(String meshyTaskId);

    Page<MeshyTask> findByStatus(MeshyTaskStatus status, Pageable pageable);

    /** A user's own tasks (the private library) — includes legacy rows with no owner.
     *  Excludes soft-deleted and EXPIRED tasks. */
    @Query("SELECT t FROM MeshyTask t WHERE (t.userId = :userId OR t.userId IS NULL) AND t.deletedAt IS NULL AND t.status <> 'EXPIRED'")
    Page<MeshyTask> findByUserIdOrLegacy(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT t FROM MeshyTask t WHERE (t.userId = :userId OR t.userId IS NULL) AND t.deletedAt IS NULL AND t.status <> 'EXPIRED' AND t.status = :status")
    Page<MeshyTask> findByUserIdOrLegacyAndStatus(@Param("userId") UUID userId,
                                                   @Param("status") MeshyTaskStatus status,
                                                   Pageable pageable);

    /** Tasks expiring within the next N days (for the "expiring soon" banner). */
    @Query("SELECT t FROM MeshyTask t WHERE (t.userId = :userId OR t.userId IS NULL) AND t.deletedAt IS NULL AND t.status = 'SUCCEEDED' AND t.expiresAt IS NOT NULL AND t.expiresAt < :threshold")
    List<MeshyTask> findExpiringSoon(@Param("userId") UUID userId, @Param("threshold") java.time.Instant threshold);

    /** Non-terminal tasks for the polling fallback to reconcile. */
    @Query("SELECT t FROM MeshyTask t WHERE t.status IN :statuses ORDER BY t.updatedAt ASC")
    List<MeshyTask> findActive(@Param("statuses") List<MeshyTaskStatus> statuses, Pageable pageable);
}
