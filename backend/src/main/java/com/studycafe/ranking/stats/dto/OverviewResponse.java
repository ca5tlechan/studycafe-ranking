package com.studycafe.ranking.stats.dto;

/** 이번 주/이번 달 총 공부시간(초). 실제 시간 — 랭킹 캡 미적용(§5.1). */
public record OverviewResponse(long weekSeconds, long monthSeconds) {
}
