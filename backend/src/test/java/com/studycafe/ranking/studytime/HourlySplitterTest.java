package com.studycafe.ranking.studytime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HourlySplitterTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static Instant kst(int day, int h, int m) {
        return LocalDateTime.of(2026, 7, day, h, m).atZone(KST).toInstant();
    }

    @Test
    @DisplayName("여러 시간에 걸침: 09:30~11:15 → 9시 30분/10시 60분/11시 15분")
    void splitsAcrossHours() {
        long[] b = HourlySplitter.split(kst(9, 9, 30), kst(9, 11, 15));
        assertEquals(30 * 60, b[9]);
        assertEquals(60 * 60, b[10]);
        assertEquals(15 * 60, b[11]);
        assertEquals(0, b[8]);
        assertEquals(0, b[12]);
    }

    @Test
    @DisplayName("한 시간 내: 14:10~14:40 → 14시 30분")
    void withinSingleHour() {
        long[] b = HourlySplitter.split(kst(9, 14, 10), kst(9, 14, 40));
        assertEquals(30 * 60, b[14]);
    }

    @Test
    @DisplayName("자정 걸침: 23:30~다음날 00:15 → 23시 30분/0시 15분")
    void crossesMidnight() {
        long[] b = HourlySplitter.split(kst(9, 23, 30), kst(10, 0, 15));
        assertEquals(30 * 60, b[23]);
        assertEquals(15 * 60, b[0]);
    }

    @Test
    @DisplayName("accumulate는 기존 버킷에 더한다(여러 세션 합산)")
    void accumulateAddsToExisting() {
        long[] b = new long[24];
        HourlySplitter.accumulate(kst(9, 10, 0), kst(9, 10, 30), b);
        HourlySplitter.accumulate(kst(9, 10, 15), kst(9, 10, 45), b);
        assertEquals(60 * 60, b[10]);
    }

    @Test
    @DisplayName("길이 0/음수 → no-op")
    void zeroOrNegativeNoop() {
        assertEquals(0, HourlySplitter.split(kst(9, 10, 0), kst(9, 10, 0))[10]);
        assertEquals(0, HourlySplitter.split(kst(9, 10, 0), kst(9, 9, 0))[9]);
    }
}
