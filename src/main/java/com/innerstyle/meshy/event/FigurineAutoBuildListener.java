package com.innerstyle.meshy.event;

import com.innerstyle.meshy.entity.enums.MeshyTaskType;
import com.innerstyle.meshy.service.MeshyTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Chibi figurine stage 2 on the server: once a prototype's SUCCEEDED state commits, start its
 * build, so the figurine finishes even if the user closed the browser. The Studio page still
 * calls POST /figurine/build when it is open; the service returns the same build for both.
 * A failure here (e.g. not enough credits) is only logged — the page shows it on its own call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FigurineAutoBuildListener {

    private final MeshyTaskService meshyTaskService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSucceeded(MeshyTaskSucceededEvent event) {
        if (event.taskType() != MeshyTaskType.FIGURE_PROTOTYPE) {
            return;
        }
        try {
            meshyTaskService.autoBuildFigurine(event.taskId());
        } catch (RuntimeException e) {
            log.warn("Auto figurine build for prototype {} not started: {}", event.taskId(), e.getMessage());
        }
    }
}
