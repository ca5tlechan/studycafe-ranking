package com.studycafe.ranking.repository;

import com.studycafe.ranking.domain.School;
import com.studycafe.ranking.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u left join fetch u.school where u.loginId = :loginId")
    Optional<User> findByLoginIdWithSchool(@Param("loginId") String loginId);

    boolean existsByLoginId(String loginId);

    int countByDisplayNameAndSchool(String displayName, School school);

    int countByDisplayNameAndSchoolIsNull(String displayName);

    /** school 까지 fetch join (open-in-view=false 환경에서 지연로딩 예외 방지). */
    @Query("select u from User u left join fetch u.school where u.id = :id")
    Optional<User> findByIdWithSchool(@Param("id") Long id);

    /** 랭킹 표시용 — 여러 유저를 학교까지 fetch join(N+1 회피). */
    @Query("select u from User u left join fetch u.school where u.id in :ids")
    List<User> findAllByIdInWithSchool(@Param("ids") Collection<Long> ids);

    /**
     * 지금 페널티 상태인 유저 id — 랭킹 제외용(§3.6c).
     * 현재 스터디-월(:ym)의 경고만 유효하므로 월이 다른 과거 경고는 자동 무시된다(lazy 리셋).
     */
    @Query("select u.id from User u where u.warningPeriodYm = :ym and u.warningCount >= :threshold")
    List<Long> findPenalizedUserIds(@Param("ym") int studyMonthYm, @Param("threshold") int threshold);

    /**
     * 04:00 자동 마감 경고 1회 원자적 적립(§3.6c). User.addWarning() 과 같은 월 리셋 규칙을 SQL 로
     * 표현한다: 저장된 월과 다르면 1로 새로 시작, 같으면 +1.
     * <p>도메인 메서드(setter) 대신 벌크 업데이트로 하는 이유: 호출부(DailyCloseService)가
     * CheckInSessionRepository.autoCloseIfActive(clearAutomatically=true) 를 먼저 쓰는데,
     * 이게 영속성 컨텍스트를 비워 User 엔티티도 detach 시킨다. detached 엔티티의 필드를 바꿔봐야
     * dirty checking 되지 않아 조용히 유실되므로, 여기서도 벌크 업데이트로 원자적으로 반영한다.
     */
    @Modifying
    @Query("update User u set "
            + "u.warningCount = case when u.warningPeriodYm = :ym then u.warningCount + 1 else 1 end, "
            + "u.warningPeriodYm = :ym "
            + "where u.id = :userId")
    void addWarning(@Param("userId") Long userId, @Param("ym") int studyMonthYm);
}
