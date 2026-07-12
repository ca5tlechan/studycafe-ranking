package com.studycafe.ranking.repository;

import com.studycafe.ranking.domain.CheckInSession;
import com.studycafe.ranking.domain.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CheckInSessionRepository extends JpaRepository<CheckInSession, Long> {

    @Query("select s from CheckInSession s where s.user.id = :userId and s.status = :status")
    Optional<CheckInSession> findByUserIdAndStatus(@Param("userId") Long userId,
                                                   @Param("status") SessionStatus status);

    /**
     * ACTIVE 세션을 원자적으로 체크아웃(WHERE status=ACTIVE). 동시 더블탭 체크아웃 시 둘째는 0건 → 멱등.
     * clearAutomatically: 벌크 update 후 영속성 컨텍스트를 비워 재조회가 최신값을 읽게 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update CheckInSession s set s.checkOutAt = :at, "
            + "s.status = com.studycafe.ranking.domain.SessionStatus.COMPLETED "
            + "where s.id = :id and s.status = com.studycafe.ranking.domain.SessionStatus.ACTIVE")
    int checkOutIfActive(@Param("id") Long id, @Param("at") Instant at);

    /** current 표시용 — 카페까지 fetch. */
    @Query("select s from CheckInSession s join fetch s.cafe where s.user.id = :userId and s.status = :status")
    Optional<CheckInSession> findByUserIdAndStatusWithCafe(@Param("userId") Long userId,
                                                           @Param("status") SessionStatus status);

    /** 유저의 '닫힌'(check_out_at != null) 세션 중 [windowStart, windowEnd) 구간과 겹치는 것 — 스터디 날짜 재계산용(§3.1). */
    @Query("select s from CheckInSession s where s.user.id = :userId and s.checkOutAt is not null "
            + "and s.checkInAt < :windowEnd and s.checkOutAt > :windowStart")
    List<CheckInSession> findClosedOverlapping(@Param("userId") Long userId,
                                               @Param("windowStart") Instant windowStart,
                                               @Param("windowEnd") Instant windowEnd);

    /**
     * 유저의 모든 닫힌 세션 — 시간대별(24h) 히스토그램 온디맨드 계산용(§5.1).
     * 히스토그램은 "전체 기간 누적"이 스펙 의미(§4)라 기간 제한을 두지 않는다.
     * §4·§5.1대로 파일럿 규모(수십 명)에선 온디맨드로 충분하며, 장기 스케일이 필요해지면
     * hourly_study_pattern 사전 집계 테이블(체크아웃 시 누적)로 전환한다.
     */
    @Query("select s from CheckInSession s where s.user.id = :userId and s.checkOutAt is not null")
    List<CheckInSession> findClosedByUserId(@Param("userId") Long userId);
}
