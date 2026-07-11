package com.studycafe.ranking.domain;

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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
