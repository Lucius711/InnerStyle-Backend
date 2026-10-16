package com.innerstyle.notification.service;

import com.innerstyle.notification.event.PrintOrderProgressEvent;

/** Tells customers about their print orders. */
public interface OrderNotificationService {

    /** Email the customer that staff updated their order's progress. Never throws. */
    void sendProgressUpdate(PrintOrderProgressEvent event);
}
