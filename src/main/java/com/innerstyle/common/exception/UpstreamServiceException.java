package com.innerstyle.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when an upstream provider (MeshyAI) returns an error or is unreachable.
 * Mapped to 502 Bad Gateway.
 */
public class UpstreamServiceException extends AppException {
    public UpstreamServiceException(String messageKey, Object... args) {
        super(messageKey, HttpStatus.BAD_GATEWAY, args);
    }
}
