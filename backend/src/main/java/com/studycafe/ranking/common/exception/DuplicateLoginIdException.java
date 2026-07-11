package com.studycafe.ranking.common.exception;

public class DuplicateLoginIdException extends RuntimeException {

    public DuplicateLoginIdException(String loginId) {
        super("이미 사용 중인 아이디입니다: " + loginId);
    }

    public DuplicateLoginIdException(String loginId, Throwable cause) {
        super("이미 사용 중인 아이디입니다: " + loginId, cause);
    }
}
