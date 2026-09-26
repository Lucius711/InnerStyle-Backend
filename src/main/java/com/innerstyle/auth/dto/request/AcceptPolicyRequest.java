package com.innerstyle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Accept the Terms & Policies version the user was shown")
public record AcceptPolicyRequest(
    @NotBlank(message = "validation.policyVersion.required") String version
) {
}
