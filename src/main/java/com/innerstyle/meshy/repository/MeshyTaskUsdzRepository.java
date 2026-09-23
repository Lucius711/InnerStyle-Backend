package com.innerstyle.meshy.repository;

import com.innerstyle.meshy.entity.MeshyTaskUsdz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Stores/loads the cached iOS AR Quick Look (USDZ) row (R2 key + metadata) for a task. */
@Repository
public interface MeshyTaskUsdzRepository extends JpaRepository<MeshyTaskUsdz, UUID> {

    // ---- legacy BYTEA -> R2 backfill ----

    @Query(value = "SELECT task_id AS taskId FROM dtb_meshy_task_usdz"
            + " WHERE storage_key IS NULL AND data IS NOT NULL LIMIT :limit", nativeQuery = true)
    List<LegacyBlobRef> findLegacy(@Param("limit") int limit);

    @Query(value = "SELECT data AS data FROM dtb_meshy_task_usdz WHERE task_id = :taskId", nativeQuery = true)
    LegacyBlobData findLegacyData(@Param("taskId") UUID taskId);

    @Modifying
    @Query(value = "UPDATE dtb_meshy_task_usdz SET storage_key = :key, data = NULL"
            + " WHERE task_id = :taskId AND storage_key IS NULL", nativeQuery = true)
    int attachKey(@Param("taskId") UUID taskId, @Param("key") String key);
}
