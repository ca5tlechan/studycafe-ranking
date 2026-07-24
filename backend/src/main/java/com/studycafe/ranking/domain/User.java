package com.studycafe.ranking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_login_id", columnNames = "login_id"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** 실명(랭킹에서는 마스킹되어 표시). §3.3 */
    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** 같은 학교 동명이인 접미 숫자(1은 미표기). §3.3 */
    @Column(name = "name_seq", nullable = false)
    private int nameSeq = 1;

    /** null → 무소속. §3.7 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    /**
     * 권한. 기본 USER. ADMIN 만 관리자 API 접근.
     * ColumnDefault('USER'): ddl-auto=update 가 컬럼을 추가할 때 DDL 에 DEFAULT 를 넣어,
     * 기존 데이터가 있는 테이블에서도 ALTER ADD COLUMN 이 기존 행을 'USER' 로 백필한다
     * (Flyway 도입 전까지 NOT NULL 컬럼 추가가 기존 DB 에서 실패하지 않게).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @org.hibernate.annotations.ColumnDefault("'USER'")
    private Role role = Role.USER;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /**
     * 04:00 자동 마감 경고 누적 횟수(§3.6c). 리셋은 배치 없이 lazy 로 한다:
     * warningPeriodYm 이 현재 스터디-월과 다르면 이 값은 무시되고(=0), 다음 적립 때 덮어쓴다.
     * 무료 호스팅 슬립으로 리셋 배치가 걸러져도 조회가 늘 정확하도록(§8.7).
     */
    @Column(name = "warning_count", nullable = false)
    private int warningCount = 0;

    /** warningCount 가 속한 스터디-월(yyyymm, 예: 202607). 0 = 아직 경고 없음. */
    @Column(name = "warning_period_ym", nullable = false)
    private int warningPeriodYm = 0;

    protected User() {
    }

    public User(String loginId, String passwordHash, String displayName, int nameSeq, School school) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.nameSeq = nameSeq;
        this.school = school;
    }

    public Long getId() {
        return id;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getNameSeq() {
        return nameSeq;
    }

    public School getSchool() {
        return school;
    }

    public Role getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** 관리자 권한 부여/회수. */
    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    /** 관리자 화면에서 소속 변경(학교 삭제 시 무소속 전환 등). */
    public void setSchool(School school) {
        this.school = school;
    }

    /** 관리자 소속 이동 — 학교와 동명이인 시퀀스(§3.3)를 새 학교 기준으로 함께 갱신한다. */
    public void moveToSchool(School school, int nameSeq) {
        this.school = school;
        this.nameSeq = nameSeq;
    }

    /** 경고 초기화(관리자 리셋 — 03:30 알림 미도달 구제, §3.6c). */
    public void resetWarnings() {
        this.warningCount = 0;
        this.warningPeriodYm = 0;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** 이 스터디-월(currentYm)에 유효한 경고 수. 월이 바뀌었으면 0(lazy 리셋). */
    public int effectiveWarnings(int currentYm) {
        return warningPeriodYm == currentYm ? warningCount : 0;
    }

    /** 경고 1회 적립. 월이 바뀌었으면 새 월로 리셋하며 1부터 시작한다. */
    public void addWarning(int currentYm) {
        if (warningPeriodYm != currentYm) {
            warningPeriodYm = currentYm;
            warningCount = 1;
        } else {
            warningCount += 1;
        }
    }

    public int getWarningCount() {
        return warningCount;
    }

    public int getWarningPeriodYm() {
        return warningPeriodYm;
    }
}
