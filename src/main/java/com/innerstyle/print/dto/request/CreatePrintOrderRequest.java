package com.innerstyle.print.dto.request;

import com.innerstyle.print.entity.enums.PrintMaterial;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Place a 3D-print order for a finished model, with recipient + shipping address")
public class CreatePrintOrderRequest {

    @NotNull(message = "1.taskId.required")
    @Schema(description = "The completed Meshy task (model) to print")
    private UUID taskId;

    @NotNull(message = "1.provider.required")
    @Pattern(regexp = "PAYOS", message = "2.provider.invalid")
    @Schema(description = "Payment method", example = "PAYOS", allowableValues = {"PAYOS"})
    private String provider;

    @NotNull(message = "1.sizeCm.required")
    @Schema(description = "Figurine height in cm (drives the price); must be a configured size",
            example = "12", allowableValues = {"8", "12", "15"})
    private Integer sizeCm;

    @NotNull(message = "1.material.required")
    @Schema(description = "Plastic to print in (adds a surcharge); must be a configured material",
            example = "PLA", allowableValues = {"PLA", "PETG", "RESIN"})
    private PrintMaterial material;

    // ----- Couple Set (optional): a second of the buyer's own models, printed alongside -----

    @Schema(description = "Couple Set: the second model to print in the same order (must be the buyer's own, finished, and differ from taskId)")
    private UUID pairTaskId;

    @Schema(description = "Couple Set: height in cm of the second figurine; required when pairTaskId is set",
            example = "12", allowableValues = {"8", "12", "15"})
    private Integer pairSizeCm;

    // ----- Recipient -----

    @NotBlank(message = "1.recipientName.required")
    @Size(max = 255, message = "2.recipientName.tooLong")
    @Schema(description = "Recipient full name", example = "Nguyen Van A")
    private String recipientName;

    @NotBlank(message = "1.recipientEmail.required")
    @Email(message = "2.recipientEmail.invalid")
    @Size(max = 255, message = "2.recipientEmail.tooLong")
    @Schema(description = "Recipient email", example = "a@example.com")
    private String recipientEmail;

    @NotBlank(message = "1.recipientPhone.required")
    @Pattern(regexp = "^[0-9+\\-\\s]{8,20}$", message = "2.recipientPhone.invalid")
    @Schema(description = "Recipient phone number", example = "0901234567")
    private String recipientPhone;

    // ----- Shipping address (Vietnam: province + ward) -----

    @NotBlank(message = "1.provinceCode.required")
    @Size(max = 20, message = "2.provinceCode.tooLong")
    @Schema(description = "Province/city code from the administrative-units API", example = "01")
    private String provinceCode;

    @NotBlank(message = "1.provinceName.required")
    @Size(max = 150, message = "2.provinceName.tooLong")
    @Schema(description = "Province/city display name", example = "Thanh pho Ha Noi")
    private String provinceName;

    @NotBlank(message = "1.wardCode.required")
    @Size(max = 20, message = "2.wardCode.tooLong")
    @Schema(description = "Ward/commune code", example = "00004")
    private String wardCode;

    @NotBlank(message = "1.wardName.required")
    @Size(max = 150, message = "2.wardName.tooLong")
    @Schema(description = "Ward/commune display name", example = "Phuong Phuc Xa")
    private String wardName;

    @NotBlank(message = "1.addressDetail.required")
    @Size(max = 500, message = "2.addressDetail.tooLong")
    @Schema(description = "House number / street / detailed address")
    private String addressDetail;

    @DecimalMin(value = "-90.0", message = "2.latitude.invalid")
    @DecimalMax(value = "90.0", message = "2.latitude.invalid")
    @Schema(description = "Optional latitude pinned on Google Maps", example = "21.028511")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0", message = "2.longitude.invalid")
    @DecimalMax(value = "180.0", message = "2.longitude.invalid")
    @Schema(description = "Optional longitude pinned on Google Maps", example = "105.804817")
    private BigDecimal longitude;

    @Size(max = 500, message = "2.note.tooLong")
    @Schema(description = "Optional note / delivery instructions")
    private String note;
}
