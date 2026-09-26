package com.innerstyle.meshy.repository;

import com.innerstyle.meshy.entity.MeshyTaskTexture;
import com.innerstyle.meshy.entity.MeshyTaskTextureId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Stores/loads cached texture-map rows (R2 key + metadata), keyed by task id + map name. */
@Repository
public interface MeshyTaskTextureRepository
        extends JpaRepository<MeshyTaskTexture, MeshyTaskTextureId> {

    /** True when some cached row of the task has a name with this prefix (e.g. a cached "model_*"). */
    boolean existsByTaskIdAndMapNameStartingWith(UUID taskId, String prefix);
}
