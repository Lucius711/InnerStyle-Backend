package com.innerstyle.print.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public 3D-print pricing, served from the single authoritative source ({@code app.print.*} /
 * {@link com.innerstyle.print.config.PrintProperties}). The frontend fetches this instead of
 * hardcoding prices (finding n2), so yml stays the one place print prices are defined.
 */
@Schema(description = "3D-print sell prices by figurine height plus the fallback fee")
public record PrintPricingResponse(
    BigDecimal fallbackFee,
    List<PrintSizePrice> sizes
) {
    public record PrintSizePrice(int heightCm, BigDecimal price) {
    }
}
