package com.innerstyle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Verify an email address with the emailed OTP code")
public class VerifyEmailRequest {

    @NotBlank(message = "1.email.required")
    @Email(message = "2.email.invalid")
    @Schema(example = "huy@example.com")
    private String email;

    @NotBlank(message = "1.otp.required")
    @Pattern(regexp = "\\d{4,9}", message = "2.otp.invalid")
    @Schema(example = "042915")
    private String otp;
}
