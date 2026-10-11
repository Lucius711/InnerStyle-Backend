package com.innerstyle.analytics.service;

import com.innerstyle.analytics.dto.response.DailyVisitResponse;
import com.innerstyle.analytics.entity.DailyVisit;
import com.innerstyle.analytics.repository.DailyVisitRepository;
import com.innerstyle.analytics.service.impl.VisitServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VisitServiceImplTest {

    private final DailyVisitRepository repository = mock(DailyVisitRepository.class);
    private final VisitService service = new VisitServiceImpl(repository);

    @Test
    void dailyVisitsFillsMissingDaysWithZeroOldestFirst() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(repository.findByVisitDateGreaterThanEqualOrderByVisitDateAsc(any()))
                .thenReturn(List.of(visit(today.minusDays(2), 9), visit(today, 10)));

        List<DailyVisitResponse> result = service.dailyVisits(3);

        assertThat(result).containsExactly(
                new DailyVisitResponse(today.minusDays(2), 9),
                new DailyVisitResponse(today.minusDays(1), 0),
                new DailyVisitResponse(today, 10));
    }

    private static DailyVisit visit(LocalDate day, int count) {
        DailyVisit v = new DailyVisit();
        ReflectionTestUtils.setField(v, "visitDate", day);
        ReflectionTestUtils.setField(v, "visitCount", count);
        return v;
    }
}
