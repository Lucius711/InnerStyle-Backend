package com.innerstyle.meshy.mapper;

import com.innerstyle.meshy.dto.response.MeshyTaskResponse;
import com.innerstyle.meshy.entity.MeshyTask;
import org.mapstruct.Mapper;

/**
 * Maps the {@link MeshyTask} entity to its API response. Never expose the entity directly
 * (see rules/03-dto-response.md).
 */
@Mapper(componentModel = "spring")
public interface MeshyTaskMapper {

    MeshyTaskResponse toResponse(MeshyTask task);
}
