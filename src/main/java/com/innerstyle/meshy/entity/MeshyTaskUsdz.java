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
 * Cached iOS AR Quick Look file (USDZ) for a task, stored out-of-line from {@link MeshyTask}.
 *
 * <p>iOS AR Quick Look only launches from a real, fetchable HTTPS URL ending in {@code .usdz} —
 * it cannot consume the in-browser {@code blob:} URL that three.js' USDZExporter produces. And
 * locally-edited models (uploaded files / a custom base added in place) have no Meshy-hosted
 * USDZ to fall back to. So the browser-built USDZ bytes are cached here and served same-origin
 * via {@code GET /tasks/{id}/model?format=usdz}.
 *
 * <p>Shares the primary key with its owning task (one USDZ per task).
 */
@Entity
@Table(name = "dtb_meshy_task_usdz")
@Getter
@Setter
@NoArgsConstructor
public class MeshyTaskUsdz {

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
