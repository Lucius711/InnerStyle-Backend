package com.innerstyle.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends AppException {
    public UnauthorizedException(String messageKey, Object... args) {
        super(messageKey, HttpStatus.UNAUTHORIZED, args);
    }
}
