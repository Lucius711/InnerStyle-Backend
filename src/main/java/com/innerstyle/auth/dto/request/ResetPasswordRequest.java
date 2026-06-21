package com.innerstyle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Set a new password using a reset token")
public class ResetPasswordRequest {

    @NotBlank(message = "1.token.required")
    private String token;

    @NotBlank(message = "1.password.required")
    @Size(min = 8, max = 72, message = "2.password.length")
    private String newPassword;
}
