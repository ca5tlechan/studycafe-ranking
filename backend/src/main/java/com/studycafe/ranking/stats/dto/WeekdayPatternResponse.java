package com.studycafe.ranking.stats.dto;

import java.util.List;

/** 요일별(월~일 7개) 누적 평균(초) — 기록 있는 해당 요일들의 하루 평균. §5.1 */
public record WeekdayPatternResponse(List<Entry> pattern) {

    public record Entry(String weekday, long avgSeconds) {
    }
}
