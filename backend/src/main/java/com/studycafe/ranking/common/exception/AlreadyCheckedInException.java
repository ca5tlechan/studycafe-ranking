package com.studycafe.ranking.common.exception;

public class AlreadyCheckedInException extends RuntimeException {

    public AlreadyCheckedInException() {
        super("이미 체크인된 상태입니다.");
    }
}
