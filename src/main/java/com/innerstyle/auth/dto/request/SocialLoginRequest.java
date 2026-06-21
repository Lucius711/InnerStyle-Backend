package com.innerstyle.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Log in / sign up with a social provider token obtained on the client")
public class SocialLoginRequest {

    @NotBlank(message = "1.token.required")
    @Schema(description = "Google ID token or Facebook access token from the client SDK")
    private String token;
}
