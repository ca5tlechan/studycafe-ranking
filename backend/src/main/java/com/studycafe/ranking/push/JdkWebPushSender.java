package com.studycafe.ranking.push;

import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * 순수 JDK 기반 Web Push 전송기 — 본문 암호화는 {@link WebPushCrypto}(RFC 8291), 인증 헤더는
 * VAPID JWT(RFC 8292, ES256 by jjwt), 전송은 {@link HttpClient}. 외부 HTTP/암호 라이브러리
 * 의존이 없어 Spring Boot 4 의 netty BOM 등과 충돌하지 않는다.
 */
public class JdkWebPushSender implements WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(JdkWebPushSender.class);
    private static final Base64.Decoder B64 = Base64.getUrlDecoder();
    /** RFC 8292: VAPID JWT 유효기간 상한은 24h. 여유 있게 12h. */
    private static final Duration JWT_TTL = Duration.ofHours(12);
    /** 푸시 서비스 보관 시간(초). 하루. */
    private static final String PUSH_TTL_SECONDS = "86400";

    private final WebPushCrypto crypto = new WebPushCrypto();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ECPrivateKey vapidPrivateKey;
    private final String vapidPublicKeyB64;
    private final String subject;

    public JdkWebPushSender(String vapidPublicKeyB64, String vapidPrivateKeyB64, String subject) {
        this.vapidPublicKeyB64 = vapidPublicKeyB64;
        this.vapidPrivateKey = WebPushCrypto.toPrivateKey(B64.decode(vapidPrivateKeyB64));
        this.subject = subject;
    }

    @Override
    public Result send(PushSubscription subscription, String payloadJson) {
        try {
            byte[] body = crypto.encrypt(
                    payloadJson.getBytes(StandardCharsets.UTF_8),
                    B64.decode(subscription.getP256dh()),
                    B64.decode(subscription.getAuth()));

            URI endpoint = URI.create(subscription.getEndpoint());
            String jwt = buildVapidJwt(originOf(endpoint));

            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .header("Authorization", "vapid t=" + jwt + ", k=" + vapidPublicKeyB64)
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", PUSH_TTL_SECONDS)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status == 404 || status == 410) {
                return Result.GONE; // 구독 만료/해지 → 정리 대상
            }
            if (status >= 200 && status < 300) {
                return Result.OK;
            }
            log.warn("[push] 전송 실패 status={} userId={}", status, subscription.getUser().getId());
            return Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        } catch (RuntimeException | java.io.IOException e) {
            log.warn("[push] 전송 오류 userId={}: {}", subscription.getUser().getId(), e.toString());
            return Result.FAILED;
        }
    }

    private String buildVapidJwt(String audience) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().add("typ", "JWT").and()
                .audience().add(audience).and()
                .subject(subject)
                .expiration(Date.from(now.plus(JWT_TTL)))
                .signWith(vapidPrivateKey, Jwts.SIG.ES256)
                .compact();
    }

    /** VAPID aud = 엔드포인트의 오리진(scheme://host[:port]), 경로 제외. */
    static String originOf(URI endpoint) {
        String origin = endpoint.getScheme() + "://" + endpoint.getHost();
        if (endpoint.getPort() != -1) {
            origin += ":" + endpoint.getPort();
        }
        return origin;
    }
}
