package com.innerstyle.membership.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Buy / upgrade a plan via direct payment")
public class SubscribeRequest {

    @NotNull(message = "1.planCode.required")
    @Pattern(regexp = "PRO|MAX", message = "2.planCode.invalid")
    @Schema(example = "PRO", allowableValues = {"PRO", "MAX"})
    private String planCode;

    @NotNull(message = "1.provider.required")
    @Pattern(regexp = "VNPAY|MOMO", message = "2.provider.invalid")
    @Schema(example = "VNPAY", allowableValues = {"VNPAY", "MOMO"})
    private String provider;
}
