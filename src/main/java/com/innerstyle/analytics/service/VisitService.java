package com.innerstyle.analytics.service;

import com.innerstyle.analytics.dto.response.DailyVisitResponse;

import java.util.List;
import java.util.UUID;

public interface VisitService {

    /** Bumps today's counter and logs who visited; {@code userId} is null for guests. */
    void recordVisit(UUID userId, String ipAddress, String userAgent);

    /** The last {@code days} days (oldest first, today last), missing days filled with 0. */
    List<DailyVisitResponse> dailyVisits(int days);

    /** Accounts in {@code dtb_users} with status ACTIVE. */
    long activeAccounts();
}
