package com.studycafe.ranking.studytime;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 세션을 KST 벽시계 시각(0~23시) 버킷별 초로 분해한다. §5.1 (24시간 시간대별 공부량 — 04:00 분할 미적용)
 * 하루 시작(04:00)과 무관하게 실제 벽시계 hour 로 쪼갠다.
 */
public final class HourlySplitter {

    private HourlySplitter() {
    }

    /** [checkIn, checkOut) 을 시간(hour) 경계로 쪼개 buckets24[0..23] 에 초를 누적한다. */
    public static void accumulate(Instant checkIn, Instant checkOut, long[] buckets24) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            return;
        }
        Instant cursor = checkIn;
        while (cursor.isBefore(checkOut)) {
            ZonedDateTime k = cursor.atZone(StudyTimePolicy.ZONE);
            Instant nextHour = k.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant();
            Instant segmentEnd = nextHour.isBefore(checkOut) ? nextHour : checkOut;
            buckets24[k.getHour()] += Duration.between(cursor, segmentEnd).toSeconds();
            cursor = segmentEnd;
        }
    }

    public static long[] split(Instant checkIn, Instant checkOut) {
        long[] buckets = new long[24];
        accumulate(checkIn, checkOut, buckets);
        return buckets;
    }
}
