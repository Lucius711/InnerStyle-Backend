package com.innerstyle.meshy.repository;

import com.innerstyle.meshy.entity.MeshyTaskUsdz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Stores/loads the cached iOS AR Quick Look (USDZ) file for a task. */
@Repository
public interface MeshyTaskUsdzRepository extends JpaRepository<MeshyTaskUsdz, UUID> {
}
