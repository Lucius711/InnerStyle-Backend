package com.innerstyle.meshy.repository;

import com.innerstyle.meshy.entity.MeshyTask;
import com.innerstyle.meshy.entity.enums.MeshyTaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeshyTaskRepository extends JpaRepository<MeshyTask, UUID> {

    Optional<MeshyTask> findByMeshyTaskId(String meshyTaskId);

    Page<MeshyTask> findByStatus(MeshyTaskStatus status, Pageable pageable);

    /** Non-terminal tasks for the polling fallback to reconcile. */
    @Query("SELECT t FROM MeshyTask t WHERE t.status IN :statuses ORDER BY t.updatedAt ASC")
    List<MeshyTask> findActive(@Param("statuses") List<MeshyTaskStatus> statuses, Pageable pageable);
}
