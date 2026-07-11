package com.studycafe.ranking.common.exception;

public class SchoolNotFoundException extends RuntimeException {

    public SchoolNotFoundException(Long schoolId) {
        super("학교를 찾을 수 없습니다: " + schoolId);
    }
}
