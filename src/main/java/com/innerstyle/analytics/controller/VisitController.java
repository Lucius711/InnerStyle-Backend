package com.innerstyle.analytics.controller;

import com.innerstyle.analytics.service.VisitService;
import com.innerstyle.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public visit beacon ({@code /api/common/visits}); the SPA calls it once per browser session. */
@Tag(name = "Visits")
@RestController
@RequestMapping("/common/visits")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;

    @Operation(summary = "Count one site visit for today")
    @PostMapping
    public ApiResponse<Void> record() {
        visitService.recordVisit();
        return ApiResponse.success("visit.recorded");
    }
}
