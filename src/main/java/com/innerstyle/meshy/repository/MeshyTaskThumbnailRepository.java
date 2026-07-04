package com.innerstyle.meshy.repository;

import com.innerstyle.meshy.entity.MeshyTaskThumbnail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Stores/loads the cached preview image (thumbnail) for a task. */
@Repository
public interface MeshyTaskThumbnailRepository extends JpaRepository<MeshyTaskThumbnail, UUID> {
}
