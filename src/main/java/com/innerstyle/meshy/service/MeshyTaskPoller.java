package com.innerstyle.meshy.service;

import com.innerstyle.meshy.client.MeshyClient;
import com.innerstyle.meshy.client.dto.MeshyTaskDto;
import com.innerstyle.meshy.config.MeshyProperties;
import com.innerstyle.meshy.entity.MeshyTask;
import com.innerstyle.meshy.entity.enums.MeshyTaskStatus;
import com.innerstyle.meshy.repository.MeshyTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fallback reconciliation: periodically polls MeshyAI for non-terminal tasks
 * and merges their
 * latest state. The webhook is the primary completion path; this guards against
 * missed callbacks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.meshy.poll", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MeshyTaskPoller {

    private static final List<MeshyTaskStatus> ACTIVE = List.of(MeshyTaskStatus.PENDING, MeshyTaskStatus.IN_PROGRESS);

    private final MeshyTaskRepository taskRepository;
    private final MeshyClient meshyClient;
    private final MeshyTaskService taskService;
    private final MeshyProperties properties;

    @Scheduled(fixedDelayString = "${app.meshy.poll.interval-ms:15000}", initialDelayString = "${app.meshy.poll.interval-ms:15000}")
    public void reconcileActiveTasks() {
        if (!properties.hasApiKey()) {
            return; // nothing to poll until a key is configured
        }
        List<MeshyTask> active = taskRepository.findActive(
                ACTIVE, PageRequest.of(0, properties.poll().batchSize()));
        if (active.isEmpty()) {
            return;
        }
        log.debug("Polling {} active Meshy task(s)", active.size());
        for (MeshyTask task : active) {
            try {
                MeshyTaskDto remote = meshyClient.getTask(task.getTaskType(), task.getMeshyTaskId());
                taskService.applyRemoteState(remote);
            } catch (RuntimeException ex) {
                log.warn("Failed to poll Meshy task {}: {}", task.getMeshyTaskId(), ex.getMessage());
            }
        }
    }
}
