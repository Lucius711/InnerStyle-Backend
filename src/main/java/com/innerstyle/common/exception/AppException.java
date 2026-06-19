package com.innerstyle.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for application exceptions. The message carries a stable, machine-readable
 * message CODE (e.g. {@code meshy.task.notFound}) which the global handler returns verbatim.
 * Localization is performed by the client (frontend i18n), so the server stores no message text.
 */
@Getter
public abstract class AppException extends RuntimeException {

    private final HttpStatus status;
    private final transient Object[] args;

    protected AppException(String messageKey, HttpStatus status, Object... args) {
        super(messageKey);
        this.status = status;
        this.args = args;
    }
}
