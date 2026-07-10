package com.studycafe.ranking.studytime;

import java.time.Duration;
import java.time.ZoneId;

/**
 * 스터디 시간 정책 상수 (튜닝 대상 — CLAUDE.md §9 미확정 값).
 * 규칙 값을 한 곳에 모아, 숫자만 바꾸면 전체에 반영되도록 한다.
 */
public final class StudyTimePolicy {

    private StudyTimePolicy() {}

    /** 모든 스터디 날짜/경계 계산 기준 시간대 (한국, DST 없음). */
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 하루의 시작 시각 = 04:00 (§3.1). */
    public static final int DAY_START_HOUR = 4;

    /** 최소 세션 필터: 이 미만 세션은 집계 제외 (§3.6d). */
    public static final Duration MIN_SESSION = Duration.ofMinutes(10);

    /** 하루 랭킹 캡: 랭킹 합산 시 하루 인정 상한 (§3.6e, A안 — 초과분만 제외). */
    public static final Duration DAILY_CAP = Duration.ofHours(16);

    /** 주간 랭킹 캡: 주간 랭킹 합계 상한 (§3.6e 백스톱). */
    public static final Duration WEEKLY_CAP = Duration.ofHours(84);

    public static long minSessionSeconds() { return MIN_SESSION.toSeconds(); }
    public static long dailyCapSeconds()   { return DAILY_CAP.toSeconds(); }
    public static long weeklyCapSeconds()  { return WEEKLY_CAP.toSeconds(); }

    /** 세션이 집계 대상인지 (최소 세션 필터 — 분할 전 세션 전체 길이 기준, §3.6d). */
    public static boolean qualifies(Duration sessionLength) {
        return sessionLength.compareTo(MIN_SESSION) >= 0;
    }

    /** 하루치 초를 하루 캡으로 자른다 (초과분만 제외, A안). 랭킹 계산 시에만 사용. */
    public static long dailyCapped(long daySeconds) {
        return Math.min(daySeconds, dailyCapSeconds());
    }

    /** 주간 합계를 주 캡으로 자른다. */
    public static long weeklyCapped(long weekSeconds) {
        return Math.min(weekSeconds, weeklyCapSeconds());
    }
}
