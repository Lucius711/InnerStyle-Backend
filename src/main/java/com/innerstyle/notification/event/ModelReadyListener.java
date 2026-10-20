package com.innerstyle.notification.event;

import com.innerstyle.meshy.event.MeshyTaskSucceededEvent;
import com.innerstyle.notification.service.ModelNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends the "model ready" email once the SUCCEEDED update has committed, off the webhook thread. */
@Component
@RequiredArgsConstructor
public class ModelReadyListener {

    private final ModelNotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSucceeded(MeshyTaskSucceededEvent event) {
        notificationService.sendModelReady(event);
    }
}
