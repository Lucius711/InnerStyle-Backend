package com.innerstyle.analytics.dto.response;

import java.time.LocalDate;

/** {@code newUsers} = accounts created in dtb_users that day. */
public record DailyVisitResponse(LocalDate date, int visits, long newUsers) {
}
