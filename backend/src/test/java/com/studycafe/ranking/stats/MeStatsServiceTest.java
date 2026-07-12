package com.studycafe.ranking.stats;

import com.studycafe.ranking.domain.Cafe;
import com.studycafe.ranking.domain.CheckInSession;
import com.studycafe.ranking.domain.DailyStudyRecord;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.CafeRepository;
import com.studycafe.ranking.repository.CheckInSessionRepository;
import com.studycafe.ranking.repository.DailyStudyRecordRepository;
import com.studycafe.ranking.repository.UserRepository;
import com.studycafe.ranking.stats.dto.CalendarResponse;
import com.studycafe.ranking.stats.dto.HourlyPatternResponse;
import com.studycafe.ranking.stats.dto.OverviewResponse;
import com.studycafe.ranking.stats.dto.WeekdayPatternResponse;
import com.studycafe.ranking.studytime.StudyClock;
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

@SpringBootTest
@Transactional
class MeStatsServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private MeStatsService service;
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
        user = userRepository.save(new User("stats_tester", "{noop}pw", "김통계", 1, null));
        cafe = cafeRepository.save(new Cafe("통계 카페", "STATS-TOKEN"));
    }

    private static Instant kst(int mo, int day, int h, int m) {
        return LocalDateTime.of(2026, mo, day, h, m).atZone(KST).toInstant();
    }

    private void daily(LocalDate date, long seconds) {
        DailyStudyRecord r = new DailyStudyRecord(user, date);
        r.setTotalSeconds(seconds);
        recordRepository.save(r);
    }

    private void closedSession(Instant in, Instant out) {
        CheckInSession s = new CheckInSession(user, cafe, in);
        s.close(out);
        sessionRepository.saveAndFlush(s);
    }

    @Test
    @DisplayName("overview: 오늘 기록이 이번 주·이번 달 합계에 포함")
    void overview() {
        LocalDate today = StudyClock.studyDateOf(Instant.now());
        daily(today, 3600);
        OverviewResponse o = service.overview(user.getId());
        assertEquals(3600, o.weekSeconds());
        assertEquals(3600, o.monthSeconds());
    }

    @Test
    @DisplayName("calendar: 해당 월 날짜별 합계(다른 달 제외)")
    void calendar() {
        daily(LocalDate.of(2026, 3, 5), 3600);
        daily(LocalDate.of(2026, 3, 10), 7200);
        daily(LocalDate.of(2026, 4, 1), 1000);
        CalendarResponse c = service.calendar(user.getId(), 2026, 3);
        assertEquals(2, c.days().size());
        assertEquals(LocalDate.of(2026, 3, 5), c.days().get(0).date());
        assertEquals(3600, c.days().get(0).totalSeconds());
        assertEquals(7200, c.days().get(1).totalSeconds());
    }

    @Test
    @DisplayName("weekday-pattern: 같은 요일 평균, 7개 반환")
    void weekdayPattern() {
        daily(LocalDate.of(2026, 3, 2), 3600);   // 월
        daily(LocalDate.of(2026, 3, 9), 10800);  // 월
        WeekdayPatternResponse w = service.weekdayPattern(user.getId());
        assertEquals(7, w.pattern().size());
        long mon = find(w, "MONDAY");
        long tue = find(w, "TUESDAY");
        assertEquals(7200, mon); // (3600+10800)/2
        assertEquals(0, tue);
    }

    private long find(WeekdayPatternResponse w, String weekday) {
        return w.pattern().stream().filter(e -> e.weekday().equals(weekday))
                .findFirst().orElseThrow().avgSeconds();
    }

    @Test
    @DisplayName("hourly-pattern: 벽시계 버킷 + 최소세션(10분) 필터, 24개 반환")
    void hourlyPattern() {
        closedSession(kst(3, 2, 9, 0), kst(3, 2, 11, 0));   // 9~11시 각 1h
        closedSession(kst(3, 2, 10, 0), kst(3, 2, 10, 5));  // 5분 → 제외
        HourlyPatternResponse h = service.hourlyPattern(user.getId());
        assertEquals(24, h.pattern().size());
        assertEquals(3600, h.pattern().get(9).totalSeconds());
        assertEquals(3600, h.pattern().get(10).totalSeconds());
    }
}
