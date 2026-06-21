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
@Schema(description = "Exchange a refresh token for a new access token")
public class RefreshTokenRequest {

    @NotBlank(message = "1.refreshToken.required")
    private String refreshToken;
}
