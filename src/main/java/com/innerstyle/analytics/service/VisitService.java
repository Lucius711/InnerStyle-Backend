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

    /** Marks a signed-in user as online now (called by the SPA heartbeat). */
    void heartbeat(UUID userId);

    /** Users whose last heartbeat is within the online window (cleared on logout). */
    long onlineUsers();
}
