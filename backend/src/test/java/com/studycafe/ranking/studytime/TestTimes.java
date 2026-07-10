package com.studycafe.ranking.studytime;

import java.time.Instant;
import java.time.LocalDateTime;

/** 테스트용 KST 시각 생성 헬퍼 (2026-07 고정). */
final class TestTimes {

    private TestTimes() {}

    /** 2026-07-{day} {hour}:{min} (KST) 의 Instant. */
    static Instant kst(int day, int hour, int min) {
        return LocalDateTime.of(2026, 7, day, hour, min)
                .atZone(StudyTimePolicy.ZONE)
                .toInstant();
    }
}
