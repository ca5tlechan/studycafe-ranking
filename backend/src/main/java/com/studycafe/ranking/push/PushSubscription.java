package com.studycafe.ranking.push;

import com.studycafe.ranking.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Web Push 구독 정보(§3.6b, §6, push_subscriptions). 03:30 사전 알림 전송 대상.
 * <p>브라우저의 PushSubscription 은 {@code endpoint} 로 유일하게 식별된다 — 같은 기기가 재구독하면
 * 같은(또는 새) endpoint 를 준다. endpoint 를 UNIQUE 로 두고 재구독 시 upsert 한다(같은 endpoint 를
 * 다른 유저가 물려받는 기기 공유/재로그인 케이스에서 소유자만 갱신).
 */
@Entity
@Table(name = "push_subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_push_subscriptions_endpoint", columnNames = "endpoint"))
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 푸시 서비스 엔드포인트 URL. 길어질 수 있어 TEXT. */
    @Column(nullable = false, columnDefinition = "text")
    private String endpoint;

    /** 구독의 공개키(P-256, base64url raw 65바이트). 메시지 암호화(RFC 8291) 대상 키. */
    @Column(nullable = false, length = 255)
    private String p256dh;

    /** 구독의 인증 시크릿(base64url 16바이트). RFC 8291 auth_secret. */
    @Column(nullable = false, length = 255)
    private String auth;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected PushSubscription() {
    }

    public PushSubscription(User user, String endpoint, String p256dh, String auth) {
        this.user = user;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    /** 같은 endpoint 재구독 — 소유자와 키를 최신으로 갱신. */
    public void refresh(User user, String p256dh, String auth) {
        this.user = user;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
