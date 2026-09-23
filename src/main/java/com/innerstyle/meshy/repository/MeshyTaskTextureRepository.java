package com.innerstyle.meshy.repository;

import com.innerstyle.meshy.entity.MeshyTaskTexture;
import com.innerstyle.meshy.entity.MeshyTaskTextureId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Stores/loads cached texture-map rows (R2 key + metadata), keyed by task id + map name. */
@Repository
public interface MeshyTaskTextureRepository
        extends JpaRepository<MeshyTaskTexture, MeshyTaskTextureId> {

    // ---- legacy BYTEA -> R2 backfill ----

    @Query(value = "SELECT task_id AS taskId, map_name AS mapName, ext AS ext, content_type AS contentType"
            + " FROM dtb_meshy_task_textures WHERE storage_key IS NULL AND data IS NOT NULL LIMIT :limit",
            nativeQuery = true)
    List<LegacyBlobRef> findLegacy(@Param("limit") int limit);

    @Query(value = "SELECT data AS data FROM dtb_meshy_task_textures WHERE task_id = :taskId AND map_name = :mapName",
            nativeQuery = true)
    LegacyBlobData findLegacyData(@Param("taskId") UUID taskId, @Param("mapName") String mapName);

    @Modifying
    @Query(value = "UPDATE dtb_meshy_task_textures SET storage_key = :key, data = NULL"
            + " WHERE task_id = :taskId AND map_name = :mapName AND storage_key IS NULL", nativeQuery = true)
    int attachKey(@Param("taskId") UUID taskId, @Param("mapName") String mapName, @Param("key") String key);
}
