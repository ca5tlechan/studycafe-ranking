package com.studycafe.ranking.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studycafe.ranking.domain.Cafe;
import com.studycafe.ranking.domain.CheckInSession;
import com.studycafe.ranking.domain.SessionStatus;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.CafeRepository;
import com.studycafe.ranking.repository.CheckInSessionRepository;
import com.studycafe.ranking.repository.DailyStudyRecordRepository;
import com.studycafe.ranking.repository.UserRepository;
import com.studycafe.ranking.studytime.StudyClock;
import com.studycafe.ranking.studytime.StudyTimePolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 04:00 자동 마감 배치 통합 검증(H2, §3.6a/§3.6c).
 * closeOverdue(now) 에 임의 now 를 주입해 04:00 경계를 태운다.
 */
@SpringBootTest
@Transactional
class DailyCloseServiceTest {

    private static final long H = 3600;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired private DailyCloseService dailyCloseService;
    @Autowired private CheckInSessionRepository sessionRepository;
    @Autowired private DailyStudyRecordRepository recordRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CafeRepository cafeRepository;

    private Cafe cafe;

    @BeforeEach
    void setUp() {
        cafe = cafeRepository.save(new Cafe("배치 카페", "BATCH-TOKEN"));
    }

    private static Instant kst(int day, int hour, int min) {
        return LocalDateTime.of(2026, 7, day, hour, min).atZone(KST).toInstant();
    }

    private User newUser(String loginId) {
        return userRepository.save(new User(loginId, "{noop}pw", "김배치", 1, null));
    }

    private CheckInSession activeSession(User u, Instant checkIn) {
        return sessionRepository.save(new CheckInSession(u, cafe, checkIn));
    }

    @Test
    @DisplayName("04:00을 넘긴 ACTIVE 세션은 그날 04:00에 AUTO_CLOSED 되고 소유자에게 경고가 쌓인다")
    void closesOverdueSessionAtBoundaryWithWarning() {
        User u = newUser("batch_a");
        // 8일 22:00 체크인 → 9일 04:00 배치 시점까지 열려 있음
        activeSession(u, kst(8, 22, 0));

        int closed = dailyCloseService.closeOverdue(kst(9, 4, 0));

        assertEquals(1, closed);
        CheckInSession s = sessionRepository.findAll().get(0);
        assertEquals(SessionStatus.AUTO_CLOSED, s.getStatus());
        // check_out_at 은 배치 실행 시각이 아니라 '그날 04:00 정각'
        assertEquals(kst(9, 4, 0), s.getCheckOutAt());
        // 8일 22:00~9일 04:00 = 6시간, 8일 스터디 날짜에 집계
        assertEquals(6 * H, recordRepository
                .findByUserIdAndStudyDate(u.getId(), LocalDate.of(2026, 7, 8)).orElseThrow().getTotalSeconds());
        // 스스로 체크아웃하지 않았으므로 경고 1회
        assertEquals(1, userRepository.findById(u.getId()).orElseThrow()
                .effectiveWarnings(StudyClock.studyMonthYm(kst(9, 4, 0))));
    }

    @Test
    @DisplayName("04:00 이후 체크인한 세션(오늘 세션)은 이번 배치의 마감 대상이 아니다")
    void doesNotCloseTodaysSession() {
        User u = newUser("batch_b");
        activeSession(u, kst(9, 5, 0)); // 9일 05:00 체크인 = 9일 스터디 날짜

        int closed = dailyCloseService.closeOverdue(kst(9, 4, 0)); // 9일 04:00 배치 (05:00보다 이전)

        // 배치 시각보다 나중 체크인은 애초에 존재하지 않지만, 방어적으로: 경계 이후 세션은 손대지 않는다
        assertEquals(0, closed);
    }

    @Test
    @DisplayName("두 번 실행해도 결과가 같다(멱등) — 이미 닫힌 세션은 다시 잡히지 않는다")
    void idempotent() {
        User u = newUser("batch_c");
        activeSession(u, kst(8, 20, 0));

        int first = dailyCloseService.closeOverdue(kst(9, 4, 0));
        int second = dailyCloseService.closeOverdue(kst(9, 4, 0));

        assertEquals(1, first);
        assertEquals(0, second); // 두 번째엔 마감할 ACTIVE 세션이 없다
        assertEquals(1, userRepository.findById(u.getId()).orElseThrow()
                .effectiveWarnings(StudyClock.studyMonthYm(kst(9, 4, 0)))); // 경고도 중복 적립되지 않음
    }

    @Test
    @DisplayName("경고가 임계값에 도달하면 penalized 로 판정된다")
    void penalizedAtThreshold() {
        User u = newUser("batch_d");
        int ym = StudyClock.studyMonthYm(kst(9, 4, 0));

        for (int i = 0; i < StudyTimePolicy.PENALTY_THRESHOLD - 1; i++) {
            u.addWarning(ym);
        }
        assertFalse(u.effectiveWarnings(ym) >= StudyTimePolicy.PENALTY_THRESHOLD);
        u.addWarning(ym);
        assertTrue(u.effectiveWarnings(ym) >= StudyTimePolicy.PENALTY_THRESHOLD);
    }

    @Test
    @DisplayName("스터디-월이 바뀌면 경고는 0으로 간주된다(lazy 리셋)")
    void warningsResetOnMonthChange() {
        User u = newUser("batch_e");
        u.addWarning(202607);
        u.addWarning(202607);

        assertEquals(2, u.effectiveWarnings(202607));
        assertEquals(0, u.effectiveWarnings(202608)); // 다음 달엔 무효
    }

    @Test
    @DisplayName("10분 미만으로 남은 세션은 집계에서 빠진다(§3.6d) — 마감·경고는 그대로")
    void tinyRemainderNotAggregated() {
        User u = newUser("batch_f");
        // 9일 03:55 체크인 → 04:00 마감 = 5분짜리(8일 스터디날짜 세그먼트 아님, 9일 03:55는 8일 날짜)
        activeSession(u, kst(9, 3, 55));

        int closed = dailyCloseService.closeOverdue(kst(9, 4, 0));

        assertEquals(1, closed);
        // 5분 < 10분 → 집계 제외
        assertNull(recordRepository
                .findByUserIdAndStudyDate(u.getId(), LocalDate.of(2026, 7, 8)).orElse(null));
        // 그래도 자동 마감이므로 경고는 적립
        assertEquals(1, userRepository.findById(u.getId()).orElseThrow()
                .effectiveWarnings(StudyClock.studyMonthYm(kst(9, 4, 0))));
    }
}
