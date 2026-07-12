package com.studycafe.ranking.domain;

/** 체크인 세션 상태. §4 */
public enum SessionStatus {
    ACTIVE,        // 공부 중(체크아웃 전)
    COMPLETED,     // 정상 체크아웃
    AUTO_CLOSED,   // 04:00 일일 자동 마감 (Phase 10)
    FORCE_CLOSED   // 세션당 상한 초과 강제 종료 (선택, Phase 10)
}
