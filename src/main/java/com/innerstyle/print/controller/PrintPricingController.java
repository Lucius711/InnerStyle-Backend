package com.innerstyle.print.controller;

import com.innerstyle.common.response.ApiResponse;
import com.innerstyle.print.config.PrintProperties;
import com.innerstyle.print.dto.response.PrintPricingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Public 3D-print pricing (finding n2): exposes the single authoritative price source
 * ({@link PrintProperties}) so the frontend can fetch it instead of hardcoding a duplicate list.
 */
@Tag(name = "Print (public)")
@RestController
@RequestMapping("/common/print")
@RequiredArgsConstructor
public class PrintPricingController {

    private final PrintProperties printProperties;

    @Operation(summary = "3D-print sell prices by figurine height (source of truth for the UI)")
    @GetMapping("/pricing")
    public ApiResponse<PrintPricingResponse> pricing() {
        List<PrintPricingResponse.PrintSizePrice> sizes = printProperties.getPrices().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new PrintPricingResponse.PrintSizePrice(e.getKey(), e.getValue()))
            .toList();
        return ApiResponse.success("print.pricing",
            new PrintPricingResponse(printProperties.getFee(), sizes));
    }
}
