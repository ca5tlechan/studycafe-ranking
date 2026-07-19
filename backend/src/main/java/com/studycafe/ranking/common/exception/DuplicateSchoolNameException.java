package com.studycafe.ranking.common.exception;

/** 학교 이름 중복(생성/수정 시). → 409. */
public class DuplicateSchoolNameException extends RuntimeException {
    public DuplicateSchoolNameException(String name) {
        super("이미 있는 학교 이름입니다: " + name);
    }

    /** DB 유니크 제약 위반을 변환할 때 원인을 보존한다(스택트레이스 유실 방지). */
    public DuplicateSchoolNameException(String name, Throwable cause) {
        super("이미 있는 학교 이름입니다: " + name, cause);
    }
}
