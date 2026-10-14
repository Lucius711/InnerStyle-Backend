package com.innerstyle.analytics.service.impl;

import com.innerstyle.analytics.dto.response.DailyVisitResponse;
import com.innerstyle.analytics.entity.DailyVisit;
import com.innerstyle.analytics.entity.VisitLog;
import com.innerstyle.analytics.repository.DailyVisitRepository;
import com.innerstyle.analytics.repository.VisitLogRepository;
import com.innerstyle.analytics.service.VisitService;
import com.innerstyle.auth.entity.enums.UserStatus;
import com.innerstyle.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VisitServiceImpl implements VisitService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int USER_AGENT_MAX = 255;
    /** Two missed 60s heartbeats = offline. */
    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(2);

    private final DailyVisitRepository repository;
    private final VisitLogRepository visitLogRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void recordVisit(UUID userId, String ipAddress, String userAgent) {
        repository.increment(LocalDate.now(ZONE));
        VisitLog log = new VisitLog();
        log.setUserId(userId);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent == null || userAgent.length() <= USER_AGENT_MAX
                ? userAgent : userAgent.substring(0, USER_AGENT_MAX));
        visitLogRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyVisitResponse> dailyVisits(int days) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, Integer> counts = repository.findByVisitDateGreaterThanEqualOrderByVisitDateAsc(from)
                .stream()
                .collect(Collectors.toMap(DailyVisit::getVisitDate, DailyVisit::getVisitCount));
        Map<LocalDate, Long> newUsers = userRepository
                .countNewUsersPerDay(from.atStartOfDay(ZONE).toInstant(), ZONE.getId())
                .stream()
                .collect(Collectors.toMap(r -> LocalDate.parse(r.getDay()), UserRepository.DailyCount::getUsers));
        return from.datesUntil(today.plusDays(1))
                .map(d -> new DailyVisitResponse(d, counts.getOrDefault(d, 0), newUsers.getOrDefault(d, 0L)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long activeAccounts() {
        return userRepository.countByStatus(UserStatus.ACTIVE);
    }

    @Override
    @Transactional
    public void heartbeat(UUID userId) {
        userRepository.touchLastSeen(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public long onlineUsers() {
        return userRepository.countSeenSince(Instant.now().minus(ONLINE_WINDOW));
    }
}
