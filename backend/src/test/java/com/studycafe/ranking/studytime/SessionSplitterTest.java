package com.studycafe.ranking.studytime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.studycafe.ranking.studytime.TestTimes.kst;
import static org.junit.jupiter.api.Assertions.*;

class SessionSplitterTest {

    private static final LocalDate D8 = LocalDate.of(2026, 7, 8);
    private static final LocalDate D9 = LocalDate.of(2026, 7, 9);
    private static final LocalDate D10 = LocalDate.of(2026, 7, 10);
    private static final long H = 3600;

    @Test
    @DisplayName("§3.1 검증 예시: 8일 18:00~9일 06:00 → 8일 10h / 9일 2h")
    void canonicalSplit() {
        List<StudySegment> segs = SessionSplitter.split(kst(8, 18, 0), kst(9, 6, 0));
        assertEquals(2, segs.size());
        assertEquals(new StudySegment(D8, 10 * H), segs.get(0));
        assertEquals(new StudySegment(D9, 2 * H), segs.get(1));
    }

    @Test
    @DisplayName("경계 미포함: 10:00~14:00 → 당일 세그먼트 1개 4h")
    void noBoundaryCross() {
        List<StudySegment> segs = SessionSplitter.split(kst(9, 10, 0), kst(9, 14, 0));
        assertEquals(List.of(new StudySegment(D9, 4 * H)), segs);
    }

    @Test
    @DisplayName("자정은 경계 아님: 8일 23:00~9일 02:00 → 전부 8일 3h")
    void crossesMidnightButNot4am() {
        List<StudySegment> segs = SessionSplitter.split(kst(8, 23, 0), kst(9, 2, 0));
        assertEquals(List.of(new StudySegment(D8, 3 * H)), segs);
    }

    @Test
    @DisplayName("시작이 정확히 04:00: 9일 04:00~06:00 → 9일 2h 한 개(분할 없음)")
    void startsExactlyAt4am() {
        List<StudySegment> segs = SessionSplitter.split(kst(9, 4, 0), kst(9, 6, 0));
        assertEquals(List.of(new StudySegment(D9, 2 * H)), segs);
    }

    @Test
    @DisplayName("끝이 정확히 04:00: 8일 20:00~9일 04:00 → 8일 8h 한 개(빈 9일 세그먼트 없음)")
    void endsExactlyAt4am() {
        List<StudySegment> segs = SessionSplitter.split(kst(8, 20, 0), kst(9, 4, 0));
        assertEquals(List.of(new StudySegment(D8, 8 * H)), segs);
    }

    @Test
    @DisplayName("3일 걸침: 8일 18:00~10일 06:00 → 8일 10h / 9일 24h / 10일 2h")
    void spansThreeStudyDates() {
        List<StudySegment> segs = SessionSplitter.split(kst(8, 18, 0), kst(10, 6, 0));
        assertEquals(3, segs.size());
        assertEquals(new StudySegment(D8, 10 * H), segs.get(0));
        assertEquals(new StudySegment(D9, 24 * H), segs.get(1));
        assertEquals(new StudySegment(D10, 2 * H), segs.get(2));
        // 합은 전체 길이(36h)와 일치해야 한다
        long total = segs.stream().mapToLong(StudySegment::seconds).sum();
        assertEquals(36 * H, total);
    }

    @Test
    @DisplayName("길이 0 → 빈 리스트")
    void zeroDuration() {
        assertTrue(SessionSplitter.split(kst(9, 10, 0), kst(9, 10, 0)).isEmpty());
    }

    @Test
    @DisplayName("음수 길이(퇴실 < 입실) → 빈 리스트")
    void negativeDuration() {
        assertTrue(SessionSplitter.split(kst(9, 10, 0), kst(9, 9, 0)).isEmpty());
    }
}
