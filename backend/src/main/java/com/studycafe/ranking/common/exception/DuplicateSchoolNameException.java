package com.studycafe.ranking.common.exception;

/** 학교 이름 중복(생성/수정 시). → 409. */
public class DuplicateSchoolNameException extends RuntimeException {
    public DuplicateSchoolNameException(String name) {
        super("이미 있는 학교 이름입니다: " + name);
    }
}
