package com.innerstyle.print.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Place a 3D-print order for a finished model")
public class CreatePrintOrderRequest {

    @NotNull(message = "1.taskId.required")
    @Schema(description = "The completed Meshy task (model) to print")
    private UUID taskId;

    @NotNull(message = "1.provider.required")
    @Pattern(regexp = "VNPAY|MOMO", message = "2.provider.invalid")
    @Schema(description = "Payment method", example = "VNPAY", allowableValues = {"VNPAY", "MOMO"})
    private String provider;

    @Size(max = 500, message = "1.note.tooLong")
    @Schema(description = "Optional note (address / instructions)")
    private String note;
}
