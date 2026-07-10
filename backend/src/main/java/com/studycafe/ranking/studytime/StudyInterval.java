package com.studycafe.ranking.studytime;

import java.time.Duration;
import java.time.Instant;

/** 체크인~체크아웃 한 구간 (원본 세션의 시간 값만 표현. 영속성과 무관한 순수 값). */
public record StudyInterval(Instant checkIn, Instant checkOut) {

    public StudyInterval {
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("checkIn/checkOut must not be null");
        }
    }

    public Duration length() {
        return Duration.between(checkIn, checkOut);
    }
}
