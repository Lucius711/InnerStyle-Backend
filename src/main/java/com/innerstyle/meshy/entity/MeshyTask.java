package com.innerstyle.meshy.entity;

import com.innerstyle.meshy.client.dto.MeshyTextureDto;
import com.innerstyle.meshy.entity.enums.MeshyTaskStatus;
import com.innerstyle.meshy.entity.enums.MeshyTaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent record of a MeshyAI task we created. We track Meshy's task id, type, status,
 * progress and the resulting asset URLs (model formats, texture/color maps, animation outputs).
 * Result URLs from Meshy are external, signed, and expire — they are stored verbatim.
 */
@Entity
@Table(name = "dtb_meshy_tasks", indexes = {
    @Index(name = "idx_dtb_meshy_tasks_meshy_task_id", columnList = "meshy_task_id", unique = true),
    @Index(name = "idx_dtb_meshy_tasks_status", columnList = "status"),
    @Index(name = "idx_dtb_meshy_tasks_parent_id", columnList = "parent_id")
})
@Getter
@Setter
@NoArgsConstructor
public class MeshyTask {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    /** The task id returned by MeshyAI. */
    @Column(name = "meshy_task_id", nullable = false, unique = true, length = 64)
    private String meshyTaskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private MeshyTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MeshyTaskStatus status = MeshyTaskStatus.PENDING;

    @Column(nullable = false)
    private int progress;

    /** Optional link to a preceding task (refine->preview, rig->source, animate->rig). */
    @Column(name = "parent_id")
    private UUID parentId;

    /** Text prompt (text-to-3d) or texture/style prompt, when applicable. */
    @Column(columnDefinition = "text")
    private String prompt;

    /** The 2D image input for image-to-3d (URL or data URI; truncated marker for data URIs). */
    @Column(name = "source_image_url", columnDefinition = "text")
    private String sourceImageUrl;

    /** Downloadable model files keyed by format (glb, fbx, obj, usdz, stl, ...). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_urls", columnDefinition = "jsonb")
    private Map<String, String> modelUrls;

    /** PBR texture/color map URLs (base_color, metallic, normal, roughness, emission). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "texture_urls", columnDefinition = "jsonb")
    private List<MeshyTextureDto> textureUrls;

    /** Rigging/animation output URLs (rigged character, walking/running, animation glb/fbx). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "animation_urls", columnDefinition = "jsonb")
    private Map<String, String> animationUrls;

    @Column(name = "thumbnail_url", columnDefinition = "text")
    private String thumbnailUrl;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "consumed_credits")
    private Integer consumedCredits;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
