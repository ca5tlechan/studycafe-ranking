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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

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
}
