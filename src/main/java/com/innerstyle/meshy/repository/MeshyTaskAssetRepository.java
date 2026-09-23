package com.innerstyle.meshy.repository;

import com.innerstyle.meshy.entity.MeshyTaskAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Stores/loads the local model row (R2 key + metadata) for a task. */
@Repository
public interface MeshyTaskAssetRepository extends JpaRepository<MeshyTaskAsset, UUID> {

    boolean existsByTaskId(UUID taskId);

    /** Whether a pre-repair backup exists, i.e. "revert to original" is possible. */
    boolean existsByTaskIdAndOriginalStorageKeyIsNotNull(UUID taskId);
}
