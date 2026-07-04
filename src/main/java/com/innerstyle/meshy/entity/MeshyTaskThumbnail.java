package com.innerstyle.meshy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Cached preview image (thumbnail) for a task, stored out-of-line from {@link MeshyTask}.
 *
 * <p>Meshy renders a thumbnail when the model is first generated, but it is never refreshed
 * after an in-place edit (e.g. a custom base/stand added via {@code POST /tasks/{id}/base}),
 * so the stored Meshy thumbnail still shows the un-edited model. The editor captures a fresh
 * PNG of the edited model and uploads it here; the task's {@code thumbnailUrl} then points at
 * this image, served same-origin via {@code GET /tasks/{id}/thumbnail}.
 *
 * <p>Shares the primary key with its owning task (one thumbnail per task).
 */
@Entity
@Table(name = "dtb_meshy_task_thumbnail")
@Getter
@Setter
@NoArgsConstructor
public class MeshyTaskThumbnail {

    /** Same id as the owning {@link MeshyTask}. */
    @Id
    @Column(name = "task_id")
    private UUID taskId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(columnDefinition = "bytea", nullable = false)
    private byte[] data;

    @Column(nullable = false)
    private long size;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
