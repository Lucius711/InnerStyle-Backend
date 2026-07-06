package com.innerstyle.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Disables a test (rather than erroring) when no Docker daemon is reachable, so Testcontainers
 * integration tests degrade to SKIPPED on machines / CI without Docker instead of failing the build.
 * Evaluated before the Testcontainers extension starts containers, so it prevents the pull attempt.
 */
public class DockerAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                return ConditionEvaluationResult.enabled("Docker is available");
            }
        } catch (Throwable ignored) {
            // fall through to disabled
        }
        return ConditionEvaluationResult.disabled(
            "Docker is not available — skipping Testcontainers integration test");
    }
}
