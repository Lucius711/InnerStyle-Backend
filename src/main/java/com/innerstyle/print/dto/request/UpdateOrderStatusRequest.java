package com.innerstyle.print.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Advance the fulfilment status of a print order (staff)")
public class UpdateOrderStatusRequest {

    @NotBlank(message = "1.status.required")
    @Pattern(regexp = "PENDING|PAID|IN_PRODUCTION|SHIPPED|COMPLETED|CANCELLED",
        message = "2.status.invalid")
    @Schema(description = "New order status", example = "IN_PRODUCTION",
        allowableValues = {"PENDING", "PAID", "IN_PRODUCTION", "SHIPPED", "COMPLETED", "CANCELLED"})
    private String status;
}
