package com.innerstyle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sign up with username + password. No email / OTP step — the account is activated
 * immediately. {@code fullName} is optional (defaults to the username).
 */
@Schema(description = "Register a new local account (username + password)")
public record RegisterRequest(

    @NotBlank(message = "1.username.required")
    @Size(min = 3, max = 50, message = "2.username.invalidLength")
    @Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "3.username.invalidFormat")
    @Schema(description = "Login username", example = "huy2915", minLength = 3, maxLength = 50)
    String username,

    @NotBlank(message = "1.password.required")
    @Size(min = 6, max = 100, message = "2.password.invalidLength")
    @Schema(description = "Account password", example = "secret123", minLength = 6, maxLength = 100)
    String password,

    @Size(min = 2, max = 255, message = "1.fullName.invalidLength")
    @Schema(description = "User full name (optional; defaults to the username)",
            example = "Nguyen Van A", minLength = 2, maxLength = 255)
    String fullName
) {
}
