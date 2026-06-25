package com.innerstyle.meshy.dto;

import com.innerstyle.meshy.dto.request.AnimateRequest;
import com.innerstyle.meshy.dto.request.ImageTo3dRequest;
import com.innerstyle.meshy.dto.request.TextTo3dRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MeshyRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    @Test
    void imageTo3dRequiresImage() {
        var dto = new ImageTo3dRequest(" ", null, null, null, null, null, null, null, null, null, null, null, null);
        assertThat(validator.validate(dto))
                .anyMatch(v -> v.getPropertyPath().toString().equals("imageUrl"));
    }

    @Test
    void imageTo3dRejectsOutOfRangePolycountAndBadPose() {
        var dto = new ImageTo3dRequest("https://x/p.png", null, null, null, null,
                10, "triangle", "k-pose", null, null, null, null, null);
        var violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("targetPolycount"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("poseMode"));
    }

    @Test
    void validImageTo3dPasses() {
        var dto = new ImageTo3dRequest("https://x/p.png", "latest", true, true, true,
                30000, "triangle", "a-pose", null, null, null, null, null);
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void textTo3dRequiresPrompt() {
        var dto = new TextTo3dRequest("", null, null, null, null, null, null);
        assertThat(validator.validate(dto))
                .anyMatch(v -> v.getPropertyPath().toString().equals("prompt"));
    }

    @Test
    void animateRequiresRigTaskAndAction() {
        var dto = new AnimateRequest(null, null, null, null);
        var violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("rigTaskId"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("actionId"));
    }

    @Test
    void animateRejectsInvalidFps() {
        var dto = new AnimateRequest(UUID.randomUUID(), 92, "change_fps", "45");
        assertThat(validator.validate(dto))
                .anyMatch(v -> v.getPropertyPath().toString().equals("fps"));
    }
}
