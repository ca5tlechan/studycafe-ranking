package com.studycafe.ranking.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 기간 → 스터디 날짜 범위 경계 검증(§3.2). */
class RankingPeriodTest {

    // 2026-03-11 = 수요일 (그 주 월요일 = 2026-03-09)
    private static final LocalDate WED = LocalDate.of(2026, 3, 11);

    @Test
    @DisplayName("this_week: 그 주 월~일")
    void thisWeek() {
        assertEquals(LocalDate.of(2026, 3, 9), RankingPeriod.THIS_WEEK.start(WED));
        assertEquals(LocalDate.of(2026, 3, 15), RankingPeriod.THIS_WEEK.end(WED));
        assertTrue(RankingPeriod.THIS_WEEK.isWeekly());
    }

    @Test
    @DisplayName("last_week: 지난주 월~일")
    void lastWeek() {
        assertEquals(LocalDate.of(2026, 3, 2), RankingPeriod.LAST_WEEK.start(WED));
        assertEquals(LocalDate.of(2026, 3, 8), RankingPeriod.LAST_WEEK.end(WED));
        assertTrue(RankingPeriod.LAST_WEEK.isWeekly());
    }

    @Test
    @DisplayName("this_month: 1일~말일")
    void thisMonth() {
        assertEquals(LocalDate.of(2026, 3, 1), RankingPeriod.THIS_MONTH.start(WED));
        assertEquals(LocalDate.of(2026, 3, 31), RankingPeriod.THIS_MONTH.end(WED));
        assertFalse(RankingPeriod.THIS_MONTH.isWeekly());
    }

    @Test
    @DisplayName("last_month: 지난달 1일~말일(2026-02=28일)")
    void lastMonth() {
        assertEquals(LocalDate.of(2026, 2, 1), RankingPeriod.LAST_MONTH.start(WED));
        assertEquals(LocalDate.of(2026, 2, 28), RankingPeriod.LAST_MONTH.end(WED));
        assertFalse(RankingPeriod.LAST_MONTH.isWeekly());
    }

    @Test
    @DisplayName("this_year: 1/1~12/31")
    void thisYear() {
        assertEquals(LocalDate.of(2026, 1, 1), RankingPeriod.THIS_YEAR.start(WED));
        assertEquals(LocalDate.of(2026, 12, 31), RankingPeriod.THIS_YEAR.end(WED));
    }

    @Test
    @DisplayName("연초 경계: this_week/last_month가 전년으로 넘어감")
    void yearBoundary() {
        LocalDate jan1 = LocalDate.of(2026, 1, 1); // 목요일
        assertEquals(LocalDate.of(2025, 12, 29), RankingPeriod.THIS_WEEK.start(jan1)); // 그 주 월 = 작년
        assertEquals(LocalDate.of(2026, 1, 4), RankingPeriod.THIS_WEEK.end(jan1));
        assertEquals(LocalDate.of(2025, 12, 1), RankingPeriod.LAST_MONTH.start(jan1));
        assertEquals(LocalDate.of(2025, 12, 31), RankingPeriod.LAST_MONTH.end(jan1));
    }
}
