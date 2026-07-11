package com.studycafe.ranking.repository;

import com.studycafe.ranking.domain.CheckInSession;
import com.studycafe.ranking.domain.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CheckInSessionRepository extends JpaRepository<CheckInSession, Long> {

    @Query("select s from CheckInSession s where s.user.id = :userId and s.status = :status")
    Optional<CheckInSession> findByUserIdAndStatus(@Param("userId") Long userId,
                                                   @Param("status") SessionStatus status);

    /** current 표시용 — 카페까지 fetch. */
    @Query("select s from CheckInSession s join fetch s.cafe where s.user.id = :userId and s.status = :status")
    Optional<CheckInSession> findByUserIdAndStatusWithCafe(@Param("userId") Long userId,
                                                           @Param("status") SessionStatus status);
}
