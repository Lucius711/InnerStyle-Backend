package com.innerstyle.meshy.repository;

import com.innerstyle.meshy.entity.MeshyTaskAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Stores/loads the local model row (R2 key + metadata) for a task. */
@Repository
public interface MeshyTaskAssetRepository extends JpaRepository<MeshyTaskAsset, UUID> {

    boolean existsByTaskId(UUID taskId);

    // ---- legacy BYTEA -> R2 backfill ----

    @Query(value = "SELECT task_id AS taskId, format AS ext, content_type AS contentType"
            + " FROM dtb_meshy_task_assets WHERE storage_key IS NULL AND data IS NOT NULL LIMIT :limit",
            nativeQuery = true)
    List<LegacyBlobRef> findLegacyCurrent(@Param("limit") int limit);

    @Query(value = "SELECT data AS data FROM dtb_meshy_task_assets WHERE task_id = :taskId", nativeQuery = true)
    LegacyBlobData findLegacyCurrentData(@Param("taskId") UUID taskId);

    @Modifying
    @Query(value = "UPDATE dtb_meshy_task_assets SET storage_key = :key, data = NULL"
            + " WHERE task_id = :taskId AND storage_key IS NULL", nativeQuery = true)
    int attachCurrentKey(@Param("taskId") UUID taskId, @Param("key") String key);

    @Query(value = "SELECT task_id AS taskId, original_format AS ext, original_content_type AS contentType"
            + " FROM dtb_meshy_task_assets"
            + " WHERE original_storage_key IS NULL AND original_data IS NOT NULL LIMIT :limit",
            nativeQuery = true)
    List<LegacyBlobRef> findLegacyOriginal(@Param("limit") int limit);

    @Query(value = "SELECT original_data AS data FROM dtb_meshy_task_assets WHERE task_id = :taskId",
            nativeQuery = true)
    LegacyBlobData findLegacyOriginalData(@Param("taskId") UUID taskId);

    @Modifying
    @Query(value = "UPDATE dtb_meshy_task_assets SET original_storage_key = :key, original_data = NULL"
            + " WHERE task_id = :taskId AND original_storage_key IS NULL", nativeQuery = true)
    int attachOriginalKey(@Param("taskId") UUID taskId, @Param("key") String key);
}
