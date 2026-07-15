package com.studycafe.ranking.ranking.dto;

import java.util.List;

/** 개인별 랭킹(§5.2). podium=1~3위, list=4~10위, myRank=내 순위(top10 밖이어도, 기록 없으면 null). */
public record IndividualRankingResponse(
        String period,
        List<RankEntry> podium,
        List<RankEntry> list,
        RankEntry myRank
) {
}
