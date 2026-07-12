package com.studycafe.ranking.common.exception;

public class InvalidCafeTokenException extends RuntimeException {

    public InvalidCafeTokenException(String cafeToken) {
        super("유효하지 않은 카페 QR입니다: " + cafeToken);
    }
}
