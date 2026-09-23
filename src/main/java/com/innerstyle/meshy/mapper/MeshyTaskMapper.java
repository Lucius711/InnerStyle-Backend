package com.innerstyle.meshy.mapper;

import com.innerstyle.meshy.dto.response.MeshyTaskResponse;
import com.innerstyle.meshy.entity.MeshyTask;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps the {@link MeshyTask} entity to its API response. Never expose the entity directly
 * (see rules/03-dto-response.md).
 */
@Mapper(componentModel = "spring")
public interface MeshyTaskMapper {

    /**
     * Always expose the thumbnail through our same-origin proxy instead of the raw Meshy CDN URL.
     * Meshy preview links are time-signed and expire, so serving them directly makes gallery
     * thumbnails break after a while; the proxy (GET /tasks/{id}/thumbnail) caches / re-fetches
     * the image server-side.
     */
    @Mapping(target = "thumbnailUrl", expression = "java(thumbnailProxyUrl(task))")
    @Mapping(target = "hasOriginalBackup", ignore = true)
    MeshyTaskResponse toResponse(MeshyTask task);

    default String thumbnailProxyUrl(MeshyTask task) {
        if (task == null || task.getThumbnailUrl() == null || task.getThumbnailUrl().isBlank()) {
            return null;
        }
        return "/api/common/3d/tasks/" + task.getId() + "/thumbnail";
    }
}
