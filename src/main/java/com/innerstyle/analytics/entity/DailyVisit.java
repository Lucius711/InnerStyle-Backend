package com.innerstyle.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Visit counter for one day; rows are only written by {@code DailyVisitRepository#increment}. */
@Entity
@Table(name = "dtb_daily_visits")
@Getter
@NoArgsConstructor
public class DailyVisit {

    @Id
    @Column(name = "visit_date")
    private LocalDate visitDate;

    @Column(name = "visit_count", nullable = false)
    private int visitCount;
}
