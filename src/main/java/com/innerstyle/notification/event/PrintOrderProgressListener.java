package com.innerstyle.notification.event;

import com.innerstyle.notification.service.OrderNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the progress email once the staff update has committed, off the request thread.
 * A plain class (no interface) so the @Async proxy exposes this method — listener methods on
 * an interface-implementing bean get a JDK proxy that hides them.
 */
@Component
@RequiredArgsConstructor
public class PrintOrderProgressListener {

    private final OrderNotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProgress(PrintOrderProgressEvent event) {
        notificationService.sendProgressUpdate(event);
    }
}
