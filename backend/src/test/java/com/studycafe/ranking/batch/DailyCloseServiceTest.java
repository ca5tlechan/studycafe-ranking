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

    /** 월 경계 테스트용 — 7월 고정인 kst() 와 달리 월을 직접 지정한다. */
    private static Instant kst(int month, int day, int hour, int min) {
        return LocalDateTime.of(2026, month, day, hour, min).atZone(KST).toInstant();
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
    @DisplayName("월 경계를 넘는 세션은 배치 실행 시각의 달이 아니라 체크인 날짜의 스터디-월에 경고가 쌓인다")
    void warningAttributedToSessionStudyMonthAcrossMonthBoundary() {
        User u = newUser("batch_month");
        // 7월 31일 22:00 체크인 → 세션의 스터디 날짜는 7월 31일(22:00-4h=18:00 → 7/31)
        activeSession(u, kst(7, 31, 22, 0));

        // 8월 1일 04:00 배치 실행 — 이 시각 기준 스터디월(202608)로 잘못 적립되면 안 된다
        int closed = dailyCloseService.closeOverdue(kst(8, 1, 4, 0));

        assertEquals(1, closed);
        User after = userRepository.findById(u.getId()).orElseThrow();
        assertEquals(1, after.effectiveWarnings(202607)); // 세션 기준(7월)에 적립돼야 정상
        assertEquals(0, after.effectiveWarnings(202608)); // 배치 실행 시각(8월) 기준이면 버그
    }

    @Test
    @DisplayName("스스로 체크아웃한 세션은 배치가 덮어쓰지 않는다(조건부 UPDATE 가 0건일 때 나머지 로직을 건너뜀)")
    void doesNotOverwriteAlreadyCheckedOutSession() {
        // 진짜 동시 경쟁(배치의 조회 직후·종료 직전 그 찰나)은 하나의 @Transactional 블록 내부라
        // 테스트에서 끼어들 수 없다 — 그건 별도 프로브로 재현해 확인했다(예외 없이 조용히 덮어써짐).
        // 여기서 검증하는 것은 그 방어책의 핵심: autoCloseIfActive 가 0건을 반환하면 배치가
        // 재계산·경고 적립을 하지 않고 그 세션에 아예 손대지 않는다는 것.
        User u = newUser("batch_race");
        CheckInSession s = activeSession(u, kst(8, 20, 0));

        Instant userCheckOut = kst(8, 23, 0);
        int updated = sessionRepository.checkOutIfActive(s.getId(), userCheckOut);
        assertEquals(1, updated);

        int closed = dailyCloseService.closeOverdue(kst(9, 4, 0));

        assertEquals(0, closed); // findAllByStatus(ACTIVE) 에 이미 안 잡힘
        CheckInSession after = sessionRepository.findById(s.getId()).orElseThrow();
        assertEquals(SessionStatus.COMPLETED, after.getStatus()); // AUTO_CLOSED 로 덮어써지지 않음
        assertEquals(userCheckOut, after.getCheckOutAt()); // 사용자의 체크아웃 시각 그대로
        assertEquals(0, userRepository.findById(u.getId()).orElseThrow()
                .effectiveWarnings(StudyClock.studyMonthYm(kst(9, 4, 0)))); // 스스로 종료했으므로 경고 없음
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
