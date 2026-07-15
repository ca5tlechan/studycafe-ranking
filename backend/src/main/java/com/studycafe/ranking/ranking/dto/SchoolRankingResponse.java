package com.studycafe.ranking.ranking.dto;

import java.util.List;

/** 학교별 랭킹(§5.2). 최소 인원(5) 미달 학교는 제외됨. */
public record SchoolRankingResponse(
        String period,
        List<SchoolEntry> podium,
        List<SchoolEntry> list
) {
}
