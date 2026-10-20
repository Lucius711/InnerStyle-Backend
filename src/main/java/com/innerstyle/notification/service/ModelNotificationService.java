package com.innerstyle.notification.service;

import com.innerstyle.meshy.event.MeshyTaskSucceededEvent;

/** Tells users their 3D model has finished generating. */
public interface ModelNotificationService {

    /** Email the owner that a long-running generation finished. Never throws. */
    void sendModelReady(MeshyTaskSucceededEvent event);
}
