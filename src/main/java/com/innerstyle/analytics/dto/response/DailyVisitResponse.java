package com.innerstyle.analytics.dto.response;

import java.time.LocalDate;

/** {@code activeUsers} = distinct signed-in users who visited that day. */
public record DailyVisitResponse(LocalDate date, int visits, long activeUsers) {
}
