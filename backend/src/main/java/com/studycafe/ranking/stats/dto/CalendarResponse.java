package com.studycafe.ranking.stats.dto;

import java.time.LocalDate;
import java.util.List;

/** 월간 캘린더 — 기록 있는 날짜만 {날짜, 총초}. 프론트에서 HH시간 MM분 포맷. §5.1 */
public record CalendarResponse(int year, int month, List<Day> days) {

    public record Day(LocalDate date, long totalSeconds) {
    }
}
