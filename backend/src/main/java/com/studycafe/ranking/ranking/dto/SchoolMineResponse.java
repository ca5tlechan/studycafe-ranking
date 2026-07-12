package com.studycafe.ranking.ranking.dto;

/** 우리 학교 페이지(§5.3). 무소속이면 available=false(안내 문구용), 그 외엔 학교 내부 개인 랭킹. */
public record SchoolMineResponse(boolean available, String schoolName, IndividualRankingResponse ranking) {

    public static SchoolMineResponse unavailable() {
        return new SchoolMineResponse(false, null, null);
    }

    public static SchoolMineResponse of(String schoolName, IndividualRankingResponse ranking) {
        return new SchoolMineResponse(true, schoolName, ranking);
    }
}
