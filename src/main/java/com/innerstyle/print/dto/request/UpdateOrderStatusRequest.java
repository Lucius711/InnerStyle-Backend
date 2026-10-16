package com.innerstyle.print.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @Size(max = 500, message = "2.note.tooLong")
    @Schema(description = "Optional update shown to the customer on the tracking timeline",
        example = "Đã in xong, đang sơn hoàn thiện")
    private String note;

    @Size(max = 100, message = "2.carrier.tooLong")
    @Schema(description = "Shipping carrier (sent as-is; blank clears it)", example = "GHN")
    private String carrier;

    @Size(max = 100, message = "2.trackingCode.tooLong")
    @Schema(description = "Carrier tracking code (sent as-is; blank clears it)", example = "GHN123456789")
    private String trackingCode;
}
