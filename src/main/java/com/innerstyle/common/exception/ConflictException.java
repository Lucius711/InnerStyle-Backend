package com.innerstyle.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends AppException {
    public ConflictException(String messageKey, Object... args) {
        super(messageKey, HttpStatus.CONFLICT, args);
    }
}
