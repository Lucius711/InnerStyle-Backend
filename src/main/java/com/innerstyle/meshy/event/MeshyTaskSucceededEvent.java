package com.innerstyle.meshy.event;

import com.innerstyle.meshy.entity.enums.MeshyTaskType;

import java.util.UUID;

/**
 * A Meshy task reached SUCCEEDED for the first time. Published inside the webhook/poll
 * transaction; listeners run only after it commits.
 */
public record MeshyTaskSucceededEvent(UUID taskId, MeshyTaskType taskType, UUID userId) {
}
