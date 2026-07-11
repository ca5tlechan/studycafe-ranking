package com.studycafe.ranking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalTime;

/** 스터디카페. 확장성 위해 분리했으나 파일럿은 1행. §4 */
@Entity
@Table(name = "cafes",
        uniqueConstraints = @UniqueConstraint(name = "uk_cafes_qr_token", columnNames = "qr_token"))
public class Cafe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** 카페 QR에 담기는 식별 토큰. */
    @Column(name = "qr_token", nullable = false)
    private String qrToken;

    /** 마감 강제 체크아웃(Phase 10, 선택)용. 파일럿(24h)에선 미사용 → nullable. */
    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected Cafe() {
    }

    public Cafe(String name, String qrToken) {
        this.name = name;
        this.qrToken = qrToken;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getQrToken() {
        return qrToken;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
