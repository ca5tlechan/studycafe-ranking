package com.studycafe.ranking.common.exception;

/** 카페를 찾을 수 없음. → 404. */
public class CafeNotFoundException extends RuntimeException {
    public CafeNotFoundException(Long id) {
        super("카페를 찾을 수 없습니다: " + id);
    }
}
