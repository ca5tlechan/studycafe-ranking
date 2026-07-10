package com.studycafe.ranking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.jwt.* 설정 바인딩. secret 은 HMAC-SHA256 기준 최소 32바이트. */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMs) {
}
