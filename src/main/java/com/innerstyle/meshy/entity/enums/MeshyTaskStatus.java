package com.innerstyle.meshy.entity.enums;

/**
 * Lifecycle status of a MeshyAI task. Mirrors the values returned by the Meshy API.
 */
public enum MeshyTaskStatus {
    PENDING,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    CANCELED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED;
    }
}
