package com.studycafe.ranking.repository;

import com.studycafe.ranking.domain.CheckInSession;
import com.studycafe.ranking.domain.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
}
