package com.studycafe.ranking.repository;

import com.studycafe.ranking.domain.DailyStudyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyStudyRecordRepository extends JpaRepository<DailyStudyRecord, Long> {

    Optional<DailyStudyRecord> findByUserIdAndStudyDate(Long userId, LocalDate studyDate);

    void deleteByUserIdAndStudyDate(Long userId, LocalDate studyDate);
}
