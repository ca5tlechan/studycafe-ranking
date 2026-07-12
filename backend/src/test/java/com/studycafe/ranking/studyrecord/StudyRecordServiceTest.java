package com.studycafe.ranking.studyrecord;

import com.studycafe.ranking.domain.Cafe;
import com.studycafe.ranking.domain.CheckInSession;
import com.studycafe.ranking.domain.DailyStudyRecord;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.CafeRepository;
import com.studycafe.ranking.repository.CheckInSessionRepository;
import com.studycafe.ranking.repository.DailyStudyRecordRepository;
import com.studycafe.ranking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 집계 로직 통합 검증(H2): 세션 종료 → 04:00 분할 → daily_study_records 재계산(멱등).
 * 크래프트한 시각으로 세션을 닫아야 하므로 CheckInSession.close(at) 를 사용한다.
 */
@SpringBootTest
@Transactional
class StudyRecordServiceTest {

    private static final long H = 3600;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private StudyRecordService studyRecordService;
    @Autowired
    private DailyStudyRecordRepository recordRepository;
    @Autowired
    private CheckInSessionRepository sessionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CafeRepository cafeRepository;

    private User user;
    private Cafe cafe;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("agg_tester", "{noop}pw", "김집계", 1, null));
        cafe = cafeRepository.save(new Cafe("집계 카페", "AGG-TOKEN"));
    }

    private static Instant kst(int day, int hour, int min) {
        return LocalDateTime.of(2026, 7, day, hour, min).atZone(KST).toInstant();
    }

    private void closedSession(Instant checkIn, Instant checkOut) {
        CheckInSession s = new CheckInSession(user, cafe, checkIn);
        s.close(checkOut);
        sessionRepository.saveAndFlush(s);
    }

    private long secondsOn(int day) {
        return recordRepository.findByUserIdAndStudyDate(user.getId(), LocalDate.of(2026, 7, day))
                .map(DailyStudyRecord::getTotalSeconds)
                .orElse(0L);
    }

    @Test
    @DisplayName("04:00 걸친 세션 → 두 스터디 날짜로 분할 집계(10h/2h)")
    void splitsAcross0400() {
        closedSession(kst(8, 18, 0), kst(9, 6, 0));
        studyRecordService.recompute(user.getId(), kst(8, 18, 0), kst(9, 6, 0));
        assertEquals(10 * H, secondsOn(8));
        assertEquals(2 * H, secondsOn(9));
    }

    @Test
    @DisplayName("같은 스터디 날짜 여러 세션 → 합산")
    void sumsMultipleSessionsSameDay() {
        closedSession(kst(9, 10, 0), kst(9, 12, 0)); // 2h
        closedSession(kst(9, 14, 0), kst(9, 15, 0)); // 1h
        studyRecordService.recompute(user.getId(), kst(9, 14, 0), kst(9, 15, 0));
        assertEquals(3 * H, secondsOn(9));
    }

    @Test
    @DisplayName("최소 세션(10분) 미만은 제외 — 레코드 미생성")
    void excludesSubMinSession() {
        closedSession(kst(9, 10, 0), kst(9, 10, 5)); // 5분
        studyRecordService.recompute(user.getId(), kst(9, 10, 0), kst(9, 10, 5));
        assertEquals(0, secondsOn(9));
        assertTrue(recordRepository.findByUserIdAndStudyDate(user.getId(), LocalDate.of(2026, 7, 9)).isEmpty());
    }

    @Test
    @DisplayName("재계산은 멱등 — 여러 번 호출해도 합계 불변(SET, 누적 아님)")
    void recomputeIsIdempotent() {
        closedSession(kst(9, 10, 0), kst(9, 13, 0)); // 3h
        studyRecordService.recompute(user.getId(), kst(9, 10, 0), kst(9, 13, 0));
        studyRecordService.recompute(user.getId(), kst(9, 10, 0), kst(9, 13, 0));
        studyRecordService.recompute(user.getId(), kst(9, 10, 0), kst(9, 13, 0));
        assertEquals(3 * H, secondsOn(9));
    }
}
