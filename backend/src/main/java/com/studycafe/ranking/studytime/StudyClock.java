package com.studycafe.ranking.studytime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * 04:00 기준 스터디 날짜/경계 계산 (전부 KST). §3.1
 * 시각은 DB의 timestamptz(=Instant/UTC)를 그대로 받아 내부에서 KST로 변환한다.
 */
public final class StudyClock {

    private StudyClock() {}

    /** 시각 T 의 스터디 날짜 = (T - 4h) 의 날짜(KST). 예: 9일 02:00 → 8일, 9일 04:00 → 9일. */
    public static LocalDate studyDateOf(Instant t) {
        return t.atZone(StudyTimePolicy.ZONE)
                .minusHours(StudyTimePolicy.DAY_START_HOUR)
                .toLocalDate();
    }

    /**
     * cursor 이후 첫 04:00 정각(KST).
     * cursor 가 00:00~03:59 이면 그날 04:00, 04:00~23:59 이면 다음 날 04:00.
     * (cursor 가 정확히 04:00이면 그 시각은 새 날의 시작이므로 다음 경계는 다음 날 04:00.)
     */
    public static Instant nextDayBoundary(Instant cursor) {
        ZonedDateTime k = cursor.atZone(StudyTimePolicy.ZONE);
        ZonedDateTime todayStart = k.toLocalDate()
                .atStartOfDay(StudyTimePolicy.ZONE)
                .plusHours(StudyTimePolicy.DAY_START_HOUR); // 그날 04:00
        ZonedDateTime boundary = k.isBefore(todayStart) ? todayStart : todayStart.plusDays(1);
        return boundary.toInstant();
    }

    /** 스터디 날짜 D 의 시작 시각 = D 04:00(KST). */
    public static Instant startOf(LocalDate studyDate) {
        return studyDate.atStartOfDay(StudyTimePolicy.ZONE)
                .plusHours(StudyTimePolicy.DAY_START_HOUR)
                .toInstant();
    }

    /** 스터디 날짜 D 의 끝(배타) = (D+1) 04:00(KST). */
    public static Instant endOf(LocalDate studyDate) {
        return startOf(studyDate.plusDays(1));
    }

    /** 시각 T 가 속한 스터디-월 (yyyymm, 예: 202607). 경고 리셋 판정용(§3.6c). */
    public static int studyMonthYm(Instant t) {
        return monthYm(studyDateOf(t));
    }

    /** 스터디 날짜 D 의 월 (yyyymm). 이미 스터디 날짜를 들고 있을 때 Instant 왕복 없이 쓴다. */
    public static int monthYm(LocalDate studyDate) {
        return studyDate.getYear() * 100 + studyDate.getMonthValue();
    }
}
