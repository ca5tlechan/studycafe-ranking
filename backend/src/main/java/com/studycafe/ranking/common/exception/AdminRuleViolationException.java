package com.studycafe.ranking.common.exception;

/** 관리자 조작 규칙 위반(예: 본인 강등/삭제 금지). → 400. */
public class AdminRuleViolationException extends RuntimeException {
    public AdminRuleViolationException(String message) {
        super(message);
    }
}
