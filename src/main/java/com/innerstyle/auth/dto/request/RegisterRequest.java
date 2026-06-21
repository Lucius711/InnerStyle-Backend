package com.innerstyle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
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
@Schema(description = "Register a new account with email + password")
public class RegisterRequest {

    @NotBlank(message = "1.email.required")
    @Email(message = "2.email.invalid")
    @Size(max = 255, message = "3.email.tooLong")
    @Schema(example = "huy@example.com")
    private String email;

    @NotBlank(message = "1.password.required")
    @Size(min = 8, max = 72, message = "2.password.length")
    @Schema(example = "S3curePass!")
    private String password;

    @NotBlank(message = "1.fullName.required")
    @Size(max = 255, message = "2.fullName.tooLong")
    @Schema(example = "Đỗ Huy")
    private String fullName;
}
