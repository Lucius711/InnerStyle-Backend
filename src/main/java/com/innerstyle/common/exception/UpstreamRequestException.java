package com.innerstyle.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when an upstream provider (MeshyAI) rejects OUR request as unprocessable (an HTTP 4xx),
 * e.g. rigging a model whose mesh cannot be pose-estimated or whose model URL is unreachable.
 * This is an input problem, not a provider outage, so it maps to 422 Unprocessable Entity (not the
 * 502 used by {@link UpstreamServiceException}) — the client can tell the user to try a different
 * model rather than "try again later".
 */
public class UpstreamRequestException extends AppException {
    public UpstreamRequestException(String messageKey, Object... args) {
        super(messageKey, HttpStatus.UNPROCESSABLE_ENTITY, args);
    }
}
