package com.innerstyle.meshy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of copying Meshy task files into R2")
public record R2BackfillResponse(int scanned, int copied, int purged, int failed) {
}
