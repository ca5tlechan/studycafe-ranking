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
                @Index(name = "ix_sessions_check_out_at", columnList = "check_out_at"),
                // findClosedOverlapping(핫 패스) 지원: user_id 필터 + check_out_at 구간
                @Index(name = "ix_sessions_user_checkout", columnList = "user_id,check_out_at")
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

    /**
     * 세션을 정상 종료(체크아웃). ACTIVE 상태에서만 허용(도메인 불변식).
     * 대화형 체크아웃의 동시성은 SessionService의 조건부 UPDATE가 담당하고,
     * 이 메서드는 단일 스레드 컨텍스트(스케줄 배치 Phase 10 · 테스트)에서 사용한다.
     */
    public void close(Instant at) {
        if (this.status != SessionStatus.ACTIVE) {
            throw new IllegalStateException("활성 상태가 아닌 세션은 종료할 수 없습니다: " + this.status);
        }
        if (at.isBefore(this.checkInAt)) {
            throw new IllegalArgumentException("체크아웃 시각이 체크인 시각보다 이릅니다.");
        }
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
