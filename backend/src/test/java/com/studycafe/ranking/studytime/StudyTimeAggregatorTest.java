package com.studycafe.ranking.studytime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.studycafe.ranking.studytime.TestTimes.kst;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StudyTimeAggregatorTest {

    private static final LocalDate D8 = LocalDate.of(2026, 7, 8);
    private static final LocalDate D9 = LocalDate.of(2026, 7, 9);
    private static final long H = 3600;

    @Test
    @DisplayName("같은 날 여러 세션 합산")
    void multiSessionSameDay_summed() {
        var sessions = List.of(
                new StudyInterval(kst(9, 10, 0), kst(9, 12, 0)),  // 2h
                new StudyInterval(kst(9, 14, 0), kst(9, 15, 0))   // 1h
        );
        Map<LocalDate, Long> totals = StudyTimeAggregator.dailyTotals(sessions);
        assertEquals(Map.of(D9, 3 * H), totals);
    }

    @Test
    @DisplayName("최소 세션 필터: 10분 미만 세션은 집계 제외")
    void minSessionFilter_excludesShortSessions() {
        var sessions = List.of(
                new StudyInterval(kst(9, 10, 0), kst(9, 10, 5)),   // 5분 → 제외
                new StudyInterval(kst(9, 11, 0), kst(9, 11, 10))   // 10분 → 포함
        );
        Map<LocalDate, Long> totals = StudyTimeAggregator.dailyTotals(sessions);
        assertEquals(Map.of(D9, 10L * 60), totals);
    }

    @Test
    @DisplayName("04:00 걸친 세션은 날짜별로 쪼개 합산")
    void dailyTotals_splitsSessionAcrossStudyDates() {
        var sessions = List.of(new StudyInterval(kst(8, 18, 0), kst(9, 6, 0))); // 10h / 2h
        Map<LocalDate, Long> totals = StudyTimeAggregator.dailyTotals(sessions);
        assertEquals(Map.of(D8, 10 * H, D9, 2 * H), totals);
    }

    @Test
    @DisplayName("랭킹 합계: 하루 캡 → 합산 → 주 캡")
    void rankingTotal_appliesDailyCapThenWeeklyCap() {
        // 하루 23h가 5일 → 각 16h로 캡 → 80h. 주 캡(84h) 미도달.
        var fiveHardDays = List.of(23 * H, 23 * H, 23 * H, 23 * H, 23 * H);
        assertEquals(80 * H, StudyTimeAggregator.rankingTotalSeconds(fiveHardDays, true));

        // 하루 16h가 6일 → 96h → 주 캡 84h.
        var sixCappedDays = List.of(16 * H, 16 * H, 16 * H, 16 * H, 16 * H, 16 * H);
        assertEquals(84 * H, StudyTimeAggregator.rankingTotalSeconds(sixCappedDays, true));
    }

    @Test
    @DisplayName("주 캡 미적용(주간 아님)이면 하루 캡만")
    void rankingTotal_withoutWeeklyCap_onlyDailyCap() {
        var days = List.of(23 * H, 10 * H); // 23→16, 10→10 = 26h
        assertEquals(26 * H, StudyTimeAggregator.rankingTotalSeconds(days, false));
    }
}
