package com.innerstyle.meshy.repository;

import com.innerstyle.meshy.entity.MeshyTaskAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Stores/loads the local model blob for a task (uploaded files and in-place edits). */
@Repository
public interface MeshyTaskAssetRepository extends JpaRepository<MeshyTaskAsset, UUID> {

    boolean existsByTaskId(UUID taskId);
}
