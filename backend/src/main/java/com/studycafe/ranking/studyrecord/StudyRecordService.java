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
import java.util.LinkedHashSet;
import java.util.List;
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

        // 조회 후 없으면 생성(check-then-act). 동일 (userId, studyDate) 동시 재계산은 Phase 3에선 발생하지 않는다:
        // 유저당 ACTIVE 세션 1개 + checkOutIfActive 조건부 UPDATE(행 잠금)로 한 유저의 체크아웃(=재계산 트리거)이 직렬화되기 때문.
        // (Phase 10 배치가 유저 체크아웃과 동시에 같은 날짜를 재계산하게 되면, 그 시점에 격리 트랜잭션 재시도로 방어한다.
        //  DB 네이티브 upsert는 H2가 ON CONFLICT 를 지원하지 않아 크로스-DB로 쓰기 어렵다.)
        DailyStudyRecord record = recordRepository.findByUserIdAndStudyDate(userId, studyDate)
                .orElseGet(() -> new DailyStudyRecord(userRepository.getReferenceById(userId), studyDate));
        record.setTotalSeconds(total);
        recordRepository.save(record);
    }
}
