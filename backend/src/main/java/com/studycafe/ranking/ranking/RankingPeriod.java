package com.studycafe.ranking.ranking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;

/**
 * 랭킹 기간(§5.2). 스터디 날짜(04:00 기준, §3.2) 범위로 환산한다.
 * 주간(this_week/last_week)만 주 84h 캡 적용 대상(§3.6e).
 */
public enum RankingPeriod {
    THIS_WEEK,
    LAST_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    THIS_YEAR;

    public boolean isWeekly() {
        return this == THIS_WEEK || this == LAST_WEEK;
    }

    public LocalDate start(LocalDate today) {
        return switch (this) {
            case THIS_WEEK -> monday(today);
            case LAST_WEEK -> monday(today).minusWeeks(1);
            case THIS_MONTH -> today.withDayOfMonth(1);
            case LAST_MONTH -> YearMonth.from(today).minusMonths(1).atDay(1);
            case THIS_YEAR -> today.withDayOfYear(1);
        };
    }

    public LocalDate end(LocalDate today) {
        return switch (this) {
            case THIS_WEEK -> monday(today).plusDays(6);
            case LAST_WEEK -> monday(today).minusWeeks(1).plusDays(6);
            case THIS_MONTH -> YearMonth.from(today).atEndOfMonth();
            case LAST_MONTH -> YearMonth.from(today).minusMonths(1).atEndOfMonth();
            case THIS_YEAR -> today.withDayOfYear(1).plusYears(1).minusDays(1);
        };
    }

    private static LocalDate monday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
