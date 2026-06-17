package com.innerstyle.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for application exceptions. The message carries an i18n message KEY which the
 * global handler resolves via {@code MessageSource} (see rules/09-error-handling.md).
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
