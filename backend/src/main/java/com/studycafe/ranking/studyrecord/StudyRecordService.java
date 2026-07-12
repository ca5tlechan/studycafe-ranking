package com.studycafe.ranking.studyrecord;

import com.studycafe.ranking.domain.DailyStudyRecord;
import com.studycafe.ranking.repository.CheckInSessionRepository;
import com.studycafe.ranking.repository.DailyStudyRecordRepository;
import com.studycafe.ranking.repository.UserRepository;
import com.studycafe.ranking.studytime.SessionSplitter;
import com.studycafe.ranking.studytime.StudyClock;
import com.studycafe.ranking.studytime.StudyInterval;
import com.studycafe.ranking.studytime.StudySegment;
import com.studycafe.ranking.studytime.StudyTimeAggregator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 세션이 닫힐 때(수동 체크아웃 / 04:00 자동 마감 / 강제 종료) 해당 세션이 영향 준 스터디 날짜를
 * 원본 세션에서 전량 재계산해 daily_study_records 에 SET 한다(멱등, §3.1).
 * 순수 분할·필터 로직은 {@code studytime/} 를 재사용한다.
 */
@Service
public class StudyRecordService {

    private final CheckInSessionRepository sessionRepository;
    private final DailyStudyRecordRepository recordRepository;
    private final UserRepository userRepository;

    public StudyRecordService(CheckInSessionRepository sessionRepository,
                              DailyStudyRecordRepository recordRepository,
                              UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.userRepository = userRepository;
    }

    /** 방금 닫힌 세션의 [checkInAt, checkOutAt] 이 걸친 스터디 날짜들을 재계산한다. */
    @Transactional
    public void recompute(Long userId, Instant checkInAt, Instant checkOutAt) {
        Set<LocalDate> affectedDates = SessionSplitter.split(checkInAt, checkOutAt).stream()
                .map(StudySegment::studyDate)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (LocalDate studyDate : affectedDates) {
            recomputeStudyDate(userId, studyDate);
        }
    }

    /** 한 스터디 날짜의 합계를 그 날짜에 걸친 유저의 모든 유효 세션에서 재계산해 SET. 0이면 레코드 삭제. */
    private void recomputeStudyDate(Long userId, LocalDate studyDate) {
        Instant windowStart = StudyClock.startOf(studyDate);
        Instant windowEnd = StudyClock.endOf(studyDate);

        List<StudyInterval> intervals = sessionRepository
                .findClosedOverlapping(userId, windowStart, windowEnd).stream()
                .map(s -> new StudyInterval(s.getCheckInAt(), s.getCheckOutAt()))
                .toList();

        long total = StudyTimeAggregator.dailyTotals(intervals).getOrDefault(studyDate, 0L);
        if (total <= 0L) {
            recordRepository.deleteByUserIdAndStudyDate(userId, studyDate);
            return;
        }

        DailyStudyRecord record = recordRepository.findByUserIdAndStudyDate(userId, studyDate)
                .orElseGet(() -> new DailyStudyRecord(userRepository.getReferenceById(userId), studyDate));
        record.setTotalSeconds(total);
        recordRepository.save(record);
    }
}
