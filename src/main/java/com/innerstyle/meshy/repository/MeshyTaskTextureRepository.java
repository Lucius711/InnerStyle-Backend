package com.innerstyle.meshy.repository;

import com.innerstyle.meshy.entity.MeshyTaskTexture;
import com.innerstyle.meshy.entity.MeshyTaskTextureId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Stores/loads cached texture-map rows (R2 key + metadata), keyed by task id + map name. */
@Repository
public interface MeshyTaskTextureRepository
        extends JpaRepository<MeshyTaskTexture, MeshyTaskTextureId> {
}
