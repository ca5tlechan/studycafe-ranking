package com.studycafe.ranking.common;

import com.studycafe.ranking.common.exception.DuplicateLoginIdException;
import com.studycafe.ranking.common.exception.InvalidCredentialsException;
import com.studycafe.ranking.common.exception.SchoolNotFoundException;
import com.studycafe.ranking.common.exception.UserNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전역 예외 → 일관된 {@link ApiError} JSON.
 * {@link ResponseEntityExceptionHandler} 를 확장해 Spring MVC 표준 예외(400/405 등)의 올바른 상태코드는 유지하면서,
 * 도메인 예외 매핑과 미매핑 예외 catch-all 을 더한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DuplicateLoginIdException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateLoginIdException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException e) {
        return build(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler({SchoolNotFoundException.class, UserNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** 검증 실패(@Valid) — Spring 기본 응답 대신 ApiError(fieldErrors)로. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "입력값이 올바르지 않습니다.",
                fieldErrors);
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** 최후의 안전망 — 위에서 매핑되지 않은 예외. 내부 메시지는 노출하지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
