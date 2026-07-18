package com.studycafe.ranking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.jwt.* 설정 바인딩. secret 은 HMAC-SHA256 기준 최소 32바이트.
 * cookieSecure: 인증 쿠키의 Secure 속성. 프로덕션(HTTPS)은 true, 로컬 http 검증 시에만 false.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMs, boolean cookieSecure) {
}
