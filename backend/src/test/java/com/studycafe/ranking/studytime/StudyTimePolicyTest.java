package com.studycafe.ranking.studytime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class StudyTimePolicyTest {

    private static final long H = 3600;

    @Test
    @DisplayName("최소 세션 필터: 10분 미만 제외, 10분 이상 포함")
    void qualifies_minSession10min() {
        assertFalse(StudyTimePolicy.qualifies(Duration.ZERO));
        assertFalse(StudyTimePolicy.qualifies(Duration.ofMinutes(9).plusSeconds(59)));
        assertTrue(StudyTimePolicy.qualifies(Duration.ofMinutes(10)));
        assertTrue(StudyTimePolicy.qualifies(Duration.ofMinutes(11)));
    }

    @Test
    @DisplayName("하루 캡(16h): 초과분만 제외(A안)")
    void dailyCap_trimsOnlyExcess() {
        assertEquals(15 * H + 59 * 60, StudyTimePolicy.dailyCapped(15 * H + 59 * 60)); // 15h59m 그대로
        assertEquals(16 * H, StudyTimePolicy.dailyCapped(16 * H));                     // 정확히 16h
        assertEquals(16 * H, StudyTimePolicy.dailyCapped(16 * H + 60));                // 16h01m → 16h
        assertEquals(16 * H, StudyTimePolicy.dailyCapped(23 * H));                     // 23h(유령 세션) → 16h
    }

    @Test
    @DisplayName("주간 캡(84h)")
    void weeklyCap() {
        assertEquals(80 * H, StudyTimePolicy.weeklyCapped(80 * H));
        assertEquals(84 * H, StudyTimePolicy.weeklyCapped(84 * H));
        assertEquals(84 * H, StudyTimePolicy.weeklyCapped(100 * H));
    }
}
