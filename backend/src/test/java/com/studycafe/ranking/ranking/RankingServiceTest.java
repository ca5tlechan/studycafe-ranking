package com.studycafe.ranking.ranking;

import com.studycafe.ranking.domain.Cafe;
import com.studycafe.ranking.domain.CheckInSession;
import com.studycafe.ranking.domain.DailyStudyRecord;
import com.studycafe.ranking.domain.School;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.ranking.dto.IndividualRankingResponse;
import com.studycafe.ranking.ranking.dto.SchoolMineResponse;
import com.studycafe.ranking.ranking.dto.SchoolRankingResponse;
import com.studycafe.ranking.repository.CafeRepository;
import com.studycafe.ranking.repository.CheckInSessionRepository;
import com.studycafe.ranking.repository.DailyStudyRecordRepository;
import com.studycafe.ranking.repository.SchoolRepository;
import com.studycafe.ranking.repository.UserRepository;
import com.studycafe.ranking.studytime.StudyClock;
import com.studycafe.ranking.studytime.StudyTimePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 랭킹 통합 검증(H2). THIS_YEAR 사용(오늘 기록이 항상 포함, 하루 캡만 적용). */
@SpringBootTest
@Transactional
class RankingServiceTest {

    private static final long H = 3600;

    @Autowired private RankingService rankingService;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private CafeRepository cafeRepository;
    @Autowired private DailyStudyRecordRepository recordRepository;
    @Autowired private CheckInSessionRepository sessionRepository;

    private LocalDate today;
    private Cafe cafe;
    private School schoolA;

    @BeforeEach
    void setUp() {
        today = StudyClock.studyDateOf(Instant.now());
        cafe = cafeRepository.save(new Cafe("랭킹카페", "RANK-CAFE"));
        schoolA = schoolRepository.save(new School("에이대학교", "에이대"));
    }

    private User user(String login, String name, int seq, School school) {
        return userRepository.save(new User(login, "{noop}pw", name, seq, school));
    }

    private void record(User u, long seconds) {
        DailyStudyRecord r = new DailyStudyRecord(u, today);
        r.setTotalSeconds(seconds);
        recordRepository.save(r);
    }

    private void session(User u, Instant in, Instant out) {
        CheckInSession s = new CheckInSession(u, cafe, in);
        s.close(out);
        sessionRepository.saveAndFlush(s);
    }

    @Test
    @DisplayName("개인: 하루 16h 캡 + 정렬 + 마스킹 + 내 순위")
    void individual_cap_order_mask() {
        User a = user("a", "김민현", 1, schoolA);
        User b = user("b", "이철수", 1, null);
        record(a, 23 * H); // 23h → 16h 캡
        record(b, 10 * H);

        IndividualRankingResponse r = rankingService.individual(a.getId(), RankingPeriod.THIS_YEAR);

        assertEquals(2, r.podium().size());
        assertEquals("김O현(에이대)", r.podium().get(0).displayName());
        assertEquals(16 * H, r.podium().get(0).seconds()); // 캡 적용
        assertTrue(r.podium().get(0).isMe());
        assertEquals("이O수(무소속)", r.podium().get(1).displayName());
        assertEquals(1, r.myRank().rank());
    }

    @Test
    @DisplayName("개인: 기록 없으면 내 순위 null")
    void individual_myRankNull_whenNoRecords() {
        record(user("a", "김민현", 1, schoolA), 3600);
        User me = user("me", "박나야", 1, null);
        IndividualRankingResponse r = rankingService.individual(me.getId(), RankingPeriod.THIS_YEAR);
        assertNull(r.myRank());
    }

    @Test
    @DisplayName("개인 동점: 마지막 체크아웃 이른 쪽 상위")
    void individual_tieBreak_earlierCheckoutHigher() {
        User a = user("a", "김민현", 1, schoolA);
        User b = user("b", "이철수", 1, schoolA);
        record(a, 3600);
        record(b, 3600); // 동점
        Instant now = Instant.now();
        session(a, now.minusSeconds(7200), now.minusSeconds(3600)); // a: 1시간 전 체크아웃(이름)
        session(b, now.minusSeconds(3600), now.minusSeconds(60));   // b: 1분 전 체크아웃(늦음)

        IndividualRankingResponse r = rankingService.individual(a.getId(), RankingPeriod.THIS_YEAR);
        assertEquals("김O현(에이대)", r.podium().get(0).displayName());
        assertEquals("이O수(에이대)", r.podium().get(1).displayName());
    }

    @Test
    @DisplayName("학교: 평균 + 최소인원(5) 미달 제외 + 무소속 제외")
    void school_average_minMembers_excludeUnaffiliated() {
        for (int i = 0; i < 5; i++) {
            record(user("a" + i, "김민현", 1, schoolA), 3600); // A 5명 3600 → 평균 3600
        }
        School b = schoolRepository.save(new School("비대학교", "비대"));
        for (int i = 0; i < 4; i++) {
            record(user("b" + i, "이철수", 1, b), 7200); // B 4명 → 미달 제외
        }
        for (int i = 0; i < 3; i++) {
            record(user("n" + i, "박무", 1, null), 9999); // 무소속 → 제외
        }

        SchoolRankingResponse r = rankingService.school(RankingPeriod.THIS_YEAR);
        assertEquals(1, r.podium().size());
        assertEquals("에이대학교", r.podium().get(0).schoolName());
        assertEquals(5, r.podium().get(0).memberCount());
        assertEquals(3600, r.podium().get(0).avgSeconds());
    }

    @Test
    @DisplayName("우리학교: 무소속이면 available=false")
    void schoolMine_unavailable_whenNoSchool() {
        User me = user("me", "박무", 1, null);
        assertFalse(rankingService.schoolMine(me.getId(), RankingPeriod.THIS_YEAR).available());
    }

    @Test
    @DisplayName("우리학교: 내 학교 소속만 필터")
    void schoolMine_filtersToMySchool() {
        User me = user("me", "김민현", 1, schoolA);
        User other = user("o", "이철수", 1, null); // 무소속
        record(me, 3600);
        record(other, 7200);

        SchoolMineResponse r = rankingService.schoolMine(me.getId(), RankingPeriod.THIS_YEAR);
        assertTrue(r.available());
        assertEquals("에이대학교", r.schoolName());
        assertEquals(1, r.ranking().podium().size()); // 무소속 other 제외, 나만
        assertTrue(r.ranking().podium().get(0).isMe());
    }

    @Test
    @DisplayName("개인: 11명 → 포디움 3 + 리스트 4~10위(7)만, 11위는 내 순위로만 표시")
    void individual_podiumAndListSlicing() {
        User me = null;
        for (int i = 0; i < 11; i++) {
            User u = user("u" + i, "김민현", 1, schoolA);
            record(u, (11 - i) * H); // u0=11h(1위) ... u10=1h(11위)
            if (i == 10) {
                me = u;
            }
        }
        IndividualRankingResponse r = rankingService.individual(me.getId(), RankingPeriod.THIS_YEAR);

        assertEquals(3, r.podium().size());
        assertEquals(7, r.list().size());       // 4~10위
        assertEquals(4, r.list().get(0).rank());
        assertEquals(10, r.list().get(6).rank());
        assertEquals(11, r.myRank().rank());    // top10 밖이어도 내 순위 표시
        assertTrue(r.myRank().isMe());
    }

    @Test
    @DisplayName("페널티(경고 3회) 유저는 개인 랭킹에서 제외된다(§3.6c)")
    void penalizedUserExcludedFromIndividual() {
        User clean = user("clean", "김정상", 1, schoolA);
        User bad = user("bad", "박페널", 1, schoolA);
        record(clean, 5 * H);
        record(bad, 20 * H); // 기록만 보면 1위여야 하지만 페널티로 제외

        int ym = StudyClock.studyMonthYm(Instant.now());
        for (int i = 0; i < StudyTimePolicy.PENALTY_THRESHOLD; i++) {
            bad.addWarning(ym);
        }
        userRepository.saveAndFlush(bad);

        IndividualRankingResponse r = rankingService.individual(clean.getId(), RankingPeriod.THIS_YEAR);
        // bad(20h)가 빠지고 clean(요청자)만 1위로 남는다
        assertEquals(1, r.podium().size());
        assertEquals(1, r.podium().get(0).rank());
        assertTrue(r.podium().get(0).isMe());
    }

    @Test
    @DisplayName("페널티 유저는 학교 랭킹 평균 분모에서도 제외된다(§3.6c) — cappedTotalsByUser 공유 회귀 방지")
    void penalizedUserExcludedFromSchoolRanking() {
        // schoolA 최소인원(5) 를 딱 채우는 정상 유저 5명 + 페널티 유저 1명(더 큰 시간, 빠져야 함)
        for (int i = 0; i < 5; i++) {
            record(user("ok" + i, "김정상", 1, schoolA), 3 * H);
        }
        User bad = user("bad2", "박페널", 1, schoolA);
        record(bad, 30 * H);
        int ym = StudyClock.studyMonthYm(Instant.now());
        for (int i = 0; i < StudyTimePolicy.PENALTY_THRESHOLD; i++) {
            bad.addWarning(ym);
        }
        userRepository.saveAndFlush(bad);

        SchoolRankingResponse r = rankingService.school(RankingPeriod.THIS_YEAR);

        assertEquals(1, r.podium().size());
        assertEquals("에이대학교", r.podium().get(0).schoolName());
        assertEquals(5, r.podium().get(0).memberCount());     // bad 제외 → 정상 5명만 분모
        assertEquals(3 * H, r.podium().get(0).avgSeconds());  // bad(30h) 포함되면 평균이 왜곡되지만 제외돼 3h 그대로
    }

    @Test
    @DisplayName("경고가 임계 미만이면 랭킹에 그대로 포함된다")
    void underThresholdStillRanked() {
        User a = user("a1", "김포함", 1, schoolA);
        record(a, 10 * H);
        int ym = StudyClock.studyMonthYm(Instant.now());
        a.addWarning(ym); // 1회 — 임계(3) 미만
        userRepository.saveAndFlush(a);

        IndividualRankingResponse r = rankingService.individual(a.getId(), RankingPeriod.THIS_YEAR);
        assertEquals(1, r.podium().size());
        assertTrue(r.myRank().isMe());
    }

    @Test
    @DisplayName("개인 주간: 하루16h×6일=96h → 주 84h 캡")
    void individual_weeklyCap() {
        User a = user("a", "김민현", 1, schoolA);
        LocalDate weekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int i = 0; i < 6; i++) { // 월~토, 모두 이번 주
            DailyStudyRecord rec = new DailyStudyRecord(a, weekMonday.plusDays(i));
            rec.setTotalSeconds(16 * H); // 하루 캡 상한
            recordRepository.save(rec);
        }
        IndividualRankingResponse r = rankingService.individual(a.getId(), RankingPeriod.THIS_WEEK);
        assertEquals(84 * H, r.podium().get(0).seconds()); // 96h → 주 84h 캡
    }
}
