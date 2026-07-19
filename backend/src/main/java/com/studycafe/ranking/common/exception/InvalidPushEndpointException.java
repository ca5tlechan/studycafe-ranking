package com.studycafe.ranking.common.exception;

/** 구독 endpoint 가 허용되지 않는 형식/대상일 때(§6 subscribe). 400 으로 매핑. */
public class InvalidPushEndpointException extends RuntimeException {

    public InvalidPushEndpointException(String message) {
        super(message);
    }
}
