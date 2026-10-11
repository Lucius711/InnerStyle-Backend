package com.innerstyle.analytics.service;

import com.innerstyle.analytics.dto.response.DailyVisitResponse;

import java.util.List;

public interface VisitService {

    void recordVisit();

    /** The last {@code days} days (oldest first, today last), missing days filled with 0. */
    List<DailyVisitResponse> dailyVisits(int days);
}
