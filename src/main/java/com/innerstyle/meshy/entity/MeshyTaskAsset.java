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
 * Authoritative local model bytes for a task, stored out-of-line from {@link MeshyTask} so the
 * (potentially several-MB) blob is never loaded by ordinary task queries. Used for:
 * <ul>
 *   <li>user-uploaded 3D files (no Meshy hosting), and</li>
 *   <li>models edited in place (e.g. a custom base added) — the edited mesh is persisted here
 *       and becomes the model served for preview / export / downstream steps.</li>
 * </ul>
 * Shares the primary key with its owning task (one asset per task).
 */
@Entity
@Table(name = "dtb_meshy_task_assets")
@Getter
@Setter
@NoArgsConstructor
public class MeshyTaskAsset {

    /** Same id as the owning {@link MeshyTask}. */
    @Id
    @Column(name = "task_id")
    private UUID taskId;

    /** Model file format/extension (e.g. {@code glb}, {@code stl}, {@code obj}). */
    @Column(nullable = false, length = 8)
    private String format;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(columnDefinition = "bytea", nullable = false)
    private byte[] data;

    @Column(nullable = false)
    private long size;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ---- pristine backup, captured lazily on the task's first repair (see
    // MeshyTaskServiceImpl#backupOriginalIfMissing). Null until then; never overwritten again,
    // so "revert to original" always restores the mesh as it was before any repair ever ran. ----

    @Column(name = "original_format", length = 8)
    private String originalFormat;

    @Column(name = "original_content_type", length = 64)
    private String originalContentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "original_data", columnDefinition = "bytea")
    private byte[] originalData;

    @Column(name = "original_size")
    private Long originalSize;
}
