package com.studycafe.ranking.ranking.dto;

/** 개인 랭킹 한 줄. seconds = 랭킹 캡 적용된 초. isMe = 현재 사용자 여부(강조용). */
public record RankEntry(int rank, String displayName, long seconds, boolean isMe) {
}
