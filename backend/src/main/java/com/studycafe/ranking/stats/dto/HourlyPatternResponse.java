package com.studycafe.ranking.stats.dto;

import java.util.List;

/** 시간대별(0~23시 24개) 총 공부량(초) — 벽시계 시각 기준(04:00 분할 미적용). §5.1 */
public record HourlyPatternResponse(List<Entry> pattern) {

    public record Entry(int hour, long totalSeconds) {
    }
}
