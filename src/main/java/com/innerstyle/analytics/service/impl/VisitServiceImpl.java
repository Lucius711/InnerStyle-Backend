package com.innerstyle.analytics.service.impl;

import com.innerstyle.analytics.dto.response.DailyVisitResponse;
import com.innerstyle.analytics.entity.DailyVisit;
import com.innerstyle.analytics.repository.DailyVisitRepository;
import com.innerstyle.analytics.service.VisitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VisitServiceImpl implements VisitService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final DailyVisitRepository repository;

    @Override
    @Transactional
    public void recordVisit() {
        repository.increment(LocalDate.now(ZONE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyVisitResponse> dailyVisits(int days) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, Integer> counts = repository.findByVisitDateGreaterThanEqualOrderByVisitDateAsc(from)
                .stream()
                .collect(Collectors.toMap(DailyVisit::getVisitDate, DailyVisit::getVisitCount));
        return from.datesUntil(today.plusDays(1))
                .map(d -> new DailyVisitResponse(d, counts.getOrDefault(d, 0)))
                .toList();
    }
}
