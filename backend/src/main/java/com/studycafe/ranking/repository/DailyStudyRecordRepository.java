package com.studycafe.ranking.repository;

import com.studycafe.ranking.domain.DailyStudyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyStudyRecordRepository extends JpaRepository<DailyStudyRecord, Long> {

    Optional<DailyStudyRecord> findByUserIdAndStudyDate(Long userId, LocalDate studyDate);

    void deleteByUserIdAndStudyDate(Long userId, LocalDate studyDate);

    /** 사용자 삭제 시 집계 기록 정리(연쇄). */
    void deleteByUserId(Long userId);

    /** 기간 총합(초). 실제 시간 — 랭킹 캡 미적용(§3.6e, 마이페이지). */
    @Query("select coalesce(sum(d.totalSeconds), 0) from DailyStudyRecord d "
            + "where d.user.id = :userId and d.studyDate between :start and :end")
    long sumSecondsBetween(@Param("userId") Long userId,
                           @Param("start") LocalDate start,
                           @Param("end") LocalDate end);

    List<DailyStudyRecord> findByUserIdAndStudyDateBetween(Long userId, LocalDate start, LocalDate end);

    /**
     * 요일별 "누적 평균"(§5.1) 계산용 — 전체 기간이 스펙 의미라 기간 제한을 두지 않는다.
     * daily_study_records 는 유저·스터디날짜당 1행이라 실질적으로 작다(하루 1행).
     */
    List<DailyStudyRecord> findByUserId(Long userId);

    /**
     * 기간 내 (userId, 하루 total_seconds) 행들 — 랭킹용. 캡(하루16h/주84h)은 서비스에서 적용(§3.6e).
     * 엔티티를 로딩하지 않는 프로젝션(N+1 회피). row[0]=userId(Long), row[1]=totalSeconds(Long).
     */
    @Query("select d.user.id, d.totalSeconds from DailyStudyRecord d where d.studyDate between :start and :end")
    List<Object[]> findUserSecondsInPeriod(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
