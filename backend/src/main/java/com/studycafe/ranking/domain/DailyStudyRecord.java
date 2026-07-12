package com.studycafe.ranking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 스터디 날짜별 집계(04:00 분할 결과). §4
 * 실제 시간 그대로 저장하고(랭킹 캡은 조회 시점에만, §3.6e), 세션이 닫힐 때 해당 날짜를 원본에서 재계산해 SET(멱등, §3.1).
 */
@Entity
@Table(name = "daily_study_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_user_date", columnNames = {"user_id", "study_date"}),
        indexes = @Index(name = "ix_daily_study_date", columnList = "study_date"))
public class DailyStudyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "study_date", nullable = false)
    private LocalDate studyDate;

    /** 실제 합계(초). 최소 세션 필터 통과분만. 랭킹 캡 미적용. */
    @Column(name = "total_seconds", nullable = false)
    private long totalSeconds;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected DailyStudyRecord() {
    }

    public DailyStudyRecord(User user, LocalDate studyDate) {
        this.user = user;
        this.studyDate = studyDate;
    }

    public void setTotalSeconds(long totalSeconds) {
        this.totalSeconds = totalSeconds;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getStudyDate() {
        return studyDate;
    }

    public long getTotalSeconds() {
        return totalSeconds;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
