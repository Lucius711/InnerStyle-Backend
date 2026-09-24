package com.innerstyle.meshy.repository;

import com.innerstyle.meshy.entity.MeshyTaskAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Stores/loads the local model row (R2 key + metadata) for a task. */
@Repository
public interface MeshyTaskAssetRepository extends JpaRepository<MeshyTaskAsset, UUID> {

    boolean existsByTaskId(UUID taskId);

    /** True only while the current model differs from its pre-repair backup (i.e. it was auto-fixed). */
    @Query("SELECT COUNT(a) > 0 FROM MeshyTaskAsset a WHERE a.taskId = :taskId"
            + " AND a.originalStorageKey IS NOT NULL AND a.storageKey <> a.originalStorageKey")
    boolean isRevertible(@Param("taskId") UUID taskId);
}
