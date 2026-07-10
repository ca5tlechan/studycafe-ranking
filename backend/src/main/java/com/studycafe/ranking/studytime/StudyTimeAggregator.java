package com.studycafe.ranking.studytime;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * 여러 세션 → 스터디 날짜별 실제 합계(초), 그리고 랭킹용 캡 적용 합계.
 *
 * 원칙(§3.6e): 저장/표시는 실제 시간 그대로, 캡(하루 16h/주 84h)은 랭킹 계산 시에만.
 * - {@link #dailyTotals}    : 최소 세션 필터(§3.6d) 통과분만 분할·합산한 "실제" 값.
 *                            daily_study_records 재계산(§3.1)의 기반.
 * - {@link #rankingTotalSeconds} : 하루 캡 → 합산 → (주간이면) 주 캡.
 */
public final class StudyTimeAggregator {

    private StudyTimeAggregator() {}

    /**
     * 세션들을 스터디 날짜별 실제 합계(초)로 집계한다. 최소 세션 필터 적용, 캡 미적용.
     * (같은 날짜에 여러 세션이 있으면 합산.)
     */
    public static Map<LocalDate, Long> dailyTotals(Collection<StudyInterval> sessions) {
        Map<LocalDate, Long> totals = new TreeMap<>();
        for (StudyInterval s : sessions) {
            if (!StudyTimePolicy.qualifies(s.length())) {
                continue; // §3.6d — 10분 미만 세션은 집계 제외
            }
            for (StudySegment seg : SessionSplitter.split(s)) {
                totals.merge(seg.studyDate(), seg.seconds(), Long::sum);
            }
        }
        return totals;
    }

    /**
     * 랭킹용 합계(초): 각 날짜에 하루 캡을 씌워 합산하고, 주간 랭킹이면 주 캡을 한 번 더 적용. §3.6e
     *
     * @param dailyRealSeconds 기간 내 날짜별 실제 합계들 (캡 미적용 값)
     * @param applyWeeklyCap   주간 랭킹이면 true (주 84h 캡 적용)
     */
    public static long rankingTotalSeconds(Collection<Long> dailyRealSeconds, boolean applyWeeklyCap) {
        long sum = 0;
        for (long daySeconds : dailyRealSeconds) {
            sum += StudyTimePolicy.dailyCapped(daySeconds);
        }
        return applyWeeklyCap ? StudyTimePolicy.weeklyCapped(sum) : sum;
    }
}
