package com.studycafe.ranking.studytime;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 세션을 04:00 경계에서 스터디 날짜별 세그먼트로 분할한다. §3.1
 *
 * 정상 운영에서는 04:00 자동 마감으로 세션이 경계를 넘지 않아 대개 세그먼트 1개(no-op)지만,
 * 배치 지연 등 예외로 경계를 넘는 세션도 정확히 귀속시키기 위한 안전망이다.
 */
public final class SessionSplitter {

    private SessionSplitter() {}

    /** 길이가 0 이하이면 빈 리스트. 그 외엔 04:00 경계마다 잘라 스터디 날짜별 세그먼트로. */
    public static List<StudySegment> split(Instant checkIn, Instant checkOut) {
        List<StudySegment> segments = new ArrayList<>();
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            return segments;
        }
        Instant cursor = checkIn;
        while (cursor.isBefore(checkOut)) {
            Instant boundary = StudyClock.nextDayBoundary(cursor);
            Instant segmentEnd = boundary.isBefore(checkOut) ? boundary : checkOut;
            long seconds = Duration.between(cursor, segmentEnd).toSeconds();
            segments.add(new StudySegment(StudyClock.studyDateOf(cursor), seconds));
            cursor = segmentEnd;
        }
        return segments;
    }

    public static List<StudySegment> split(StudyInterval interval) {
        return split(interval.checkIn(), interval.checkOut());
    }
}
