package com.innerstyle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Log in with username + password.
 */
@Schema(description = "Log in with username + password")
public record LoginRequest(

    @NotBlank(message = "1.username.required")
    @Schema(description = "Login username", example = "huy2915")
    String username,

    @NotBlank(message = "1.password.required")
    @Schema(description = "Account password", example = "secret123")
    String password
) {
}
