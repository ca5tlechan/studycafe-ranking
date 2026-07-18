package com.studycafe.ranking.auth;

import com.studycafe.ranking.config.JwtProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 인증 JWT 를 담는 쿠키를 만든다(이슈 #7 — localStorage 대신 HttpOnly 쿠키).
 *
 * <p><b>HttpOnly</b>: JS 로 읽을 수 없어 XSS 로 토큰이 통째로 유출되는 것을 막는다.
 * <p><b>SameSite=Strict</b>: 크로스사이트 요청에는 쿠키가 실리지 않아 CSRF 를 구조적으로 차단한다.
 * 이 앱은 단일 오리진 PWA 라 별도 CSRF 토큰 없이 Strict 로 충분하다(같은 오리진 fetch 는 SameSite 와
 * 무관하게 쿠키가 실리므로 정상 동작). 배포가 프론트/백엔드 오리진 분리로 가면 SameSite=None 이
 * 필요해지고, 그때는 CSRF 토큰을 함께 도입해야 한다(§2, 이슈 #7).
 * <p><b>Secure</b>: 프로덕션(HTTPS)에서 true. 로컬 http 검증 시에만 설정으로 끈다.
 */
@Component
public class AuthCookieFactory {

    public static final String COOKIE_NAME = "scr_token";

    private final long maxAgeSeconds;
    private final boolean secure;

    public AuthCookieFactory(JwtProperties props) {
        this.maxAgeSeconds = props.expirationMs() / 1000; // 토큰 만료와 쿠키 수명을 맞춘다
        this.secure = props.cookieSecure();
    }

    /** 로그인 성공 시 내려주는 인증 쿠키. */
    public ResponseCookie create(String token) {
        return base(token).maxAge(maxAgeSeconds).build();
    }

    /** 로그아웃 시 쿠키 제거(maxAge=0 → 즉시 만료). */
    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/");
    }
}
