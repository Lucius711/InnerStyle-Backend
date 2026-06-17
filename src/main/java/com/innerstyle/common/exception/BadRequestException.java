package com.innerstyle.common.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends AppException {
    public BadRequestException(String messageKey, Object... args) {
        super(messageKey, HttpStatus.BAD_REQUEST, args);
    }
}
