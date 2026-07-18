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

    /** 04:00 자동 마감 배치용 — 아직 열려 있는 세션 전부. user 를 fetch 해 소유자 경고 적립에 쓴다. */
    @Query("select s from CheckInSession s join fetch s.user where s.status = :status")
    List<CheckInSession> findAllByStatus(@Param("status") SessionStatus status);

    /**
     * ACTIVE 세션을 원자적으로 체크아웃(WHERE status=ACTIVE). 동시 더블탭 체크아웃 시 둘째는 0건 → 멱등.
     * clearAutomatically: 벌크 update 후 영속성 컨텍스트를 비워 재조회가 최신값을 읽게 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update CheckInSession s set s.checkOutAt = :at, "
            + "s.status = com.studycafe.ranking.domain.SessionStatus.COMPLETED "
            + "where s.id = :id and s.status = com.studycafe.ranking.domain.SessionStatus.ACTIVE")
    int checkOutIfActive(@Param("id") Long id, @Param("at") Instant at);

    /**
     * 04:00 배치의 원자적 자동 마감(WHERE status=ACTIVE) — checkOutIfActive 와 동일 패턴.
     * findAllByStatus(ACTIVE) 로 세션을 로드한 뒤 이걸로 실제 종료를 확정한다: 그 사이 사용자가
     * 스스로 체크아웃했다면(경쟁) 0건이 되어 배치는 그 세션에 손대지 않는다.
     * <p>{@code clearAutomatically = true} 가 필요하다: 이 벌크 UPDATE 는 DB row 는 바꾸지만
     * findAllByStatus 로 이미 로드해 1차 캐시(identity map)에 있는 세션 엔티티는 갱신하지 않는다.
     * clear() 없이 바로 이어서 재계산(StudyRecordService.recompute → findClosedOverlapping)이
     * 같은 세션을 다시 조회하면, Hibernate 가 DB의 최신 값 대신 캐시의 stale 객체(checkOutAt=null)를
     * 돌려줘 검증 예외가 난다(실제로 재현해 확인함). clear() 는 User 엔티티도 detach 시키므로,
     * 경고 적립은 도메인 메서드(setter) 대신 UserRepository.addWarning() 벌크 업데이트로 한다.
     * <p>{@code flushAutomatically = true} 도 명시한다: 이게 없어도(기본 false) 실제로는
     * Hibernate 의 FlushModeType.AUTO 가 이 벌크 쿼리 실행 전에 dirty 엔티티(직전 세션의
     * recompute() 가 만든 DailyStudyRecord 등)를 이미 자동 flush 하므로 유실은 재현되지
     * 않았다 — 여러 유저를 한 배치 트랜잭션에서 처리하는 시나리오로 직접 확인함. 다만 이 안전성이
     * Hibernate 내부 동작에 우연히 기대고 있어, Spring Data 레벨에서도 명시적으로 보장한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CheckInSession s set s.checkOutAt = :at, "
            + "s.status = com.studycafe.ranking.domain.SessionStatus.AUTO_CLOSED "
            + "where s.id = :id and s.status = com.studycafe.ranking.domain.SessionStatus.ACTIVE")
    int autoCloseIfActive(@Param("id") Long id, @Param("at") Instant at);

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

    /** 기간 내 유저별 마지막 체크아웃 시각 — 개인 랭킹 동점 tie-break(§3.5). row[0]=userId, row[1]=Instant. */
    @Query("select s.user.id, max(s.checkOutAt) from CheckInSession s "
            + "where s.checkOutAt >= :start and s.checkOutAt < :end group by s.user.id")
    List<Object[]> lastCheckoutByUser(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * 유저의 모든 닫힌 세션 — 시간대별(24h) 히스토그램 온디맨드 계산용(§5.1).
     * 히스토그램은 "전체 기간 누적"이 스펙 의미(§4)라 기간 제한을 두지 않는다.
     * §4·§5.1대로 파일럿 규모(수십 명)에선 온디맨드로 충분하며, 장기 스케일이 필요해지면
     * hourly_study_pattern 사전 집계 테이블(체크아웃 시 누적)로 전환한다.
     */
    @Query("select s from CheckInSession s where s.user.id = :userId and s.checkOutAt is not null")
    List<CheckInSession> findClosedByUserId(@Param("userId") Long userId);
}
