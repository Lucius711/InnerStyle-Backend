package com.innerstyle.meshy.controller;

import com.innerstyle.common.exception.UnauthorizedException;
import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.meshy.client.dto.MeshyTaskDto;
import com.innerstyle.meshy.config.MeshyProperties;
import com.innerstyle.meshy.service.MeshyTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

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
public class MeshyWebhookController {

    private static final String SECRET_HEADER = "X-Webhook-Secret";
    /**
     * Publicly-known placeholder shipped in .env.example; must never be used as a
     * real secret.
     */
    private static final String DEFAULT_SECRET = "change_me_random_secret";
    private static final List<String> DEV_PROFILES = List.of("dev", "test", "local");

    private final MeshyTaskService meshyTaskService;
    private final MeshyProperties properties;
    private final Environment environment;

    public MeshyWebhookController(MeshyTaskService meshyTaskService, MeshyProperties properties,
            Environment environment) {
        this.meshyTaskService = meshyTaskService;
        this.properties = properties;
        this.environment = environment;
    }

    @PostMapping("/webhooks/meshy")
    @Operation(summary = "MeshyAI task status callback")
    public ApiResponse<Void> handle(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody MeshyTaskDto payload) {
        log.info("Meshy webhook received, X-Webhook-Secret present={}", secret != null);
        verifySecret(secret);
        meshyTaskService.applyRemoteState(payload);
        return ApiResponse.success("meshy.webhook.received");
    }

    /**
     * Fail closed outside dev/test/local (finding TRUNG): an unset or still-default
     * secret used
     * to be accepted unconditionally, letting anyone forge task-completion
     * callbacks. The
     * scheduled poller (see class javadoc) is an existing fallback completion path,
     * so rejecting
     * unverifiable webhook calls here is safe — no state update is lost, only
     * delayed.
     */
    private void verifySecret(String provided) {
        String expected = properties.webhookSecret();
        boolean unconfigured = expected == null || expected.isBlank() || DEFAULT_SECRET.equals(expected.trim());
        if (unconfigured) {
            if (devProfileActive()) {
                log.warn("Meshy webhook secret is not configured; accepting callback without "
                        + "verification. INSECURE outside dev/test.");
                return;
            }
            throw new UnauthorizedException("meshy.webhook.unauthorized");
        }
        if (provided == null || !constantTimeEquals(expected, provided)) {
            throw new UnauthorizedException("meshy.webhook.unauthorized");
        }
    }

    private boolean devProfileActive() {
        return environment.getActiveProfiles().length == 0
                || Arrays.stream(environment.getActiveProfiles())
                        .anyMatch(p -> DEV_PROFILES.contains(p.toLowerCase()));
    }

    private boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
