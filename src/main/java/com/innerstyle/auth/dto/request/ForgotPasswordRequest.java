package com.innerstyle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request a password-reset email")
public class ForgotPasswordRequest {

    @NotBlank(message = "1.email.required")
    @Email(message = "2.email.invalid")
    @Schema(example = "huy@example.com")
    private String email;
}
