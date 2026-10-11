package com.innerstyle.analytics.dto.response;

import java.time.LocalDate;

public record DailyVisitResponse(LocalDate date, int visits) {
}
