package com.innerstyle.common.exception;

import org.springframework.http.HttpStatus;

/** The caller is authenticated but not allowed to perform the action (HTTP 403). */
public class ForbiddenException extends AppException {
    public ForbiddenException(String messageKey, Object... args) {
        super(messageKey, HttpStatus.FORBIDDEN, args);
    }
}
