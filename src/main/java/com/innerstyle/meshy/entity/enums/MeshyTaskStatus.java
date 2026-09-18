package com.innerstyle.meshy.entity.enums;

/**
 * Lifecycle status of a MeshyAI task. Mirrors the values returned by the Meshy API.
 */
public enum MeshyTaskStatus {
    PENDING,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    CANCELED,
    /** Meshy deleted this task from their servers (tasks older than ~60 days). */
    EXPIRED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED || this == EXPIRED;
    }

    public boolean isExpired() {
        return this == EXPIRED;
    }
}
