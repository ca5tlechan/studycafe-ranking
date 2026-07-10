package com.studycafe.ranking.studytime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.studycafe.ranking.studytime.TestTimes.kst;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StudyClockTest {

    private static final LocalDate D8 = LocalDate.of(2026, 7, 8);
    private static final LocalDate D9 = LocalDate.of(2026, 7, 9);

    @Test
    @DisplayName("04:00 이전은 전날 스터디 날짜")
    void studyDate_before4am_isPreviousDay() {
        assertEquals(D8, StudyClock.studyDateOf(kst(9, 2, 0)));
        assertEquals(D8, StudyClock.studyDateOf(kst(9, 3, 59)));
    }

    @Test
    @DisplayName("04:00 정각은 새 스터디 날짜")
    void studyDate_at4am_isSameDay() {
        assertEquals(D9, StudyClock.studyDateOf(kst(9, 4, 0)));
    }

    @Test
    @DisplayName("04:00 이후는 당일 스터디 날짜")
    void studyDate_after4am_isSameDay() {
        assertEquals(D9, StudyClock.studyDateOf(kst(9, 5, 0)));
        assertEquals(D9, StudyClock.studyDateOf(kst(9, 23, 59)));
    }

    @Test
    @DisplayName("경계: 새벽(02:00) → 그날 04:00")
    void nextBoundary_earlyMorning_isToday4am() {
        assertEquals(kst(9, 4, 0), StudyClock.nextDayBoundary(kst(9, 2, 0)));
    }

    @Test
    @DisplayName("경계: 정각 04:00 → 다음 날 04:00")
    void nextBoundary_exactly4am_isTomorrow4am() {
        assertEquals(kst(10, 4, 0), StudyClock.nextDayBoundary(kst(9, 4, 0)));
    }

    @Test
    @DisplayName("경계: 오후(20:00) → 다음 날 04:00")
    void nextBoundary_afternoon_isTomorrow4am() {
        assertEquals(kst(10, 4, 0), StudyClock.nextDayBoundary(kst(9, 20, 0)));
    }
}
