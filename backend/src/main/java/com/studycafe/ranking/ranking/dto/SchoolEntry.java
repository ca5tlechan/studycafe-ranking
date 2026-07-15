package com.studycafe.ranking.ranking.dto;

/** 학교 랭킹 한 줄. avgSeconds = 활동 인원 평균(랭킹 캡 적용), memberCount = 그 기간 활동 인원. */
public record SchoolEntry(int rank, String schoolName, int memberCount, long avgSeconds) {
}
