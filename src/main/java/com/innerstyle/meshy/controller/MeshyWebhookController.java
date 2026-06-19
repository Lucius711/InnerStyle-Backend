package com.innerstyle.meshy.controller;

import com.innerstyle.common.exception.UnauthorizedException;
import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.meshy.client.dto.MeshyTaskDto;
import com.innerstyle.meshy.config.MeshyProperties;
import com.innerstyle.meshy.service.MeshyTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives MeshyAI webhook callbacks when a task changes state. This is the
 * PRIMARY
 * completion path; the scheduled poller is a fallback. Configure this
 * endpoint's public URL
 * in the Meshy dashboard and protect it with the shared
 * {@code MESHY_WEBHOOK_SECRET}.
 *
 * <p>
 * This path is unauthenticated at the gateway level (it is an external
 * callback), so we
 * verify a shared secret header here instead.
 */
@Slf4j
@Tag(name = "Webhooks - MeshyAI")
@RestController
@RequiredArgsConstructor
public class MeshyWebhookController {

    private static final String SECRET_HEADER = "X-Webhook-Secret";

    private final MeshyTaskService meshyTaskService;
    private final MeshyProperties properties;

    @PostMapping("/webhooks/meshy")
    @Operation(summary = "MeshyAI task status callback")
    public ApiResponse<Void> handle(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody MeshyTaskDto payload) {
        verifySecret(secret);
        meshyTaskService.applyRemoteState(payload);
        return ApiResponse.success("meshy.webhook.received");
    }

    private void verifySecret(String provided) {
        String expected = properties.webhookSecret();
        if (expected == null || expected.isBlank()) {
            // No secret configured -> accept (development). Configure MESHY_WEBHOOK_SECRET
            // in prod.
            log.warn("Meshy webhook secret is not configured; accepting callback without verification");
            return;
        }
        if (!expected.equals(provided)) {
            throw new UnauthorizedException("meshy.webhook.unauthorized");
        }
    }
}
