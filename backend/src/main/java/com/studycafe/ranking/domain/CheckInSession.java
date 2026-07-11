package com.studycafe.ranking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 원본 체크인 세션 = 진실의 원천(§4).
 * 유저당 ACTIVE 세션은 최대 1개(부분 유니크 인덱스 ux_active_session_per_user — Postgres에서 생성).
 */
@Entity
@Table(name = "check_in_sessions",
        indexes = {
                @Index(name = "ix_sessions_user_status", columnList = "user_id,status"),
                @Index(name = "ix_sessions_check_out_at", columnList = "check_out_at")
        })
public class CheckInSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cafe_id", nullable = false)
    private Cafe cafe;

    @Column(name = "check_in_at", nullable = false)
    private Instant checkInAt;

    /** 활성 중엔 null. */
    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected CheckInSession() {
    }

    public CheckInSession(User user, Cafe cafe, Instant checkInAt) {
        this.user = user;
        this.cafe = cafe;
        this.checkInAt = checkInAt;
        this.status = SessionStatus.ACTIVE;
    }

    /** 정상 체크아웃. */
    public void checkOut(Instant at) {
        this.checkOutAt = at;
        this.status = SessionStatus.COMPLETED;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Cafe getCafe() {
        return cafe;
    }

    public Instant getCheckInAt() {
        return checkInAt;
    }

    public Instant getCheckOutAt() {
        return checkOutAt;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
