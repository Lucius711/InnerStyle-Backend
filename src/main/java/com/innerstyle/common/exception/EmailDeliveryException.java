package com.innerstyle.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when the SMTP provider (e.g. Gmail) rejects or fails to deliver a transactional email.
 * Mapped to 502 Bad Gateway — the failure is in an upstream mail service, not the request.
 */
public class EmailDeliveryException extends AppException {
    public EmailDeliveryException(String messageKey, Object... args) {
        super(messageKey, HttpStatus.BAD_GATEWAY, args);
    }
}
