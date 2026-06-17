package com.innerstyle.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AppException {
    public ResourceNotFoundException(String messageKey, Object... args) {
        super(messageKey, HttpStatus.NOT_FOUND, args);
    }
}
