package com.studycafe.ranking.auth;

import com.studycafe.ranking.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 인증은 됐으나 권한이 부족한 요청(예: 일반 유저가 /api/admin/**)에 대해 403 ApiError JSON 을 반환한다.
 * 명시하지 않으면 일부 구성에서 인증 엔트리포인트(401)로 처리돼, 클라이언트가 세션 만료로 오인하고
 * 로그아웃해 버린다. 401(미인증)과 403(권한 없음)을 정확히 구분하기 위한 핸들러.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiError body = ApiError.of(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "접근 권한이 없습니다.");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
