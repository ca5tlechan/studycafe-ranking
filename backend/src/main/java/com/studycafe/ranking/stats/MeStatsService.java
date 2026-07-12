package com.studycafe.ranking.stats;

import com.studycafe.ranking.domain.CheckInSession;
import com.studycafe.ranking.domain.DailyStudyRecord;
import com.studycafe.ranking.repository.CheckInSessionRepository;
import com.studycafe.ranking.repository.DailyStudyRecordRepository;
import com.studycafe.ranking.stats.dto.CalendarResponse;
import com.studycafe.ranking.stats.dto.HourlyPatternResponse;
import com.studycafe.ranking.stats.dto.OverviewResponse;
import com.studycafe.ranking.stats.dto.WeekdayPatternResponse;
import com.studycafe.ranking.studytime.HourlySplitter;
import com.studycafe.ranking.studytime.StudyClock;
import com.studycafe.ranking.studytime.StudyTimePolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 마이페이지 통계(§5.1). 모두 실제 시간 그대로 — 랭킹 캡(하루16h/주84h)은 랭킹에만 적용(§3.6e).
 * 주/월/요일은 daily_study_records(04:00 스터디 날짜) 기반, 24시간 히스토그램은 원본 세션의 벽시계 시각 기반.
 */
@Service
@Transactional(readOnly = true)
public class MeStatsService {

    private final DailyStudyRecordRepository recordRepository;
    private final CheckInSessionRepository sessionRepository;

    public MeStatsService(DailyStudyRecordRepository recordRepository,
                          CheckInSessionRepository sessionRepository) {
        this.recordRepository = recordRepository;
        this.sessionRepository = sessionRepository;
    }

    /** 이번 주(월요일~오늘)·이번 달(1일~오늘) 총합. 스터디 날짜 기준(§3.2). */
    public OverviewResponse overview(Long userId) {
        LocalDate today = StudyClock.studyDateOf(Instant.now());
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);
        long weekSeconds = recordRepository.sumSecondsBetween(userId, weekStart, today);
        long monthSeconds = recordRepository.sumSecondsBetween(userId, monthStart, today);
        return new OverviewResponse(weekSeconds, monthSeconds);
    }

    /** 해당 연월의 날짜별 합계(기록 있는 날만). */
    public CalendarResponse calendar(Long userId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        List<CalendarResponse.Day> days = recordRepository
                .findByUserIdAndStudyDateBetween(userId, yearMonth.atDay(1), yearMonth.atEndOfMonth()).stream()
                .sorted(Comparator.comparing(DailyStudyRecord::getStudyDate))
                .map(d -> new CalendarResponse.Day(d.getStudyDate(), d.getTotalSeconds()))
                .toList();
        return new CalendarResponse(year, month, days);
    }

    /** 요일별(월~일) 누적 평균 — 기록 있는 해당 요일 스터디날짜들의 하루 평균. */
    public WeekdayPatternResponse weekdayPattern(Long userId) {
        Map<DayOfWeek, long[]> acc = new EnumMap<>(DayOfWeek.class); // [합, 개수]
        for (DailyStudyRecord d : recordRepository.findByUserId(userId)) {
            long[] sc = acc.computeIfAbsent(d.getStudyDate().getDayOfWeek(), k -> new long[2]);
            sc[0] += d.getTotalSeconds();
            sc[1] += 1;
        }
        List<WeekdayPatternResponse.Entry> pattern = new ArrayList<>();
        for (DayOfWeek dow : DayOfWeek.values()) { // MONDAY..SUNDAY
            long[] sc = acc.get(dow);
            long avg = (sc == null || sc[1] == 0) ? 0 : Math.round((double) sc[0] / sc[1]);
            pattern.add(new WeekdayPatternResponse.Entry(dow.name(), avg));
        }
        return new WeekdayPatternResponse(pattern);
    }

    /** 시간대별(0~23시) 총량 — 벽시계 시각, 04:00 분할 미적용. 최소 세션(10분) 필터는 적용. */
    public HourlyPatternResponse hourlyPattern(Long userId) {
        long[] buckets = new long[24];
        for (CheckInSession s : sessionRepository.findClosedByUserId(userId)) {
            Duration length = Duration.between(s.getCheckInAt(), s.getCheckOutAt());
            if (StudyTimePolicy.qualifies(length)) {
                HourlySplitter.accumulate(s.getCheckInAt(), s.getCheckOutAt(), buckets);
            }
        }
        List<HourlyPatternResponse.Entry> pattern = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            pattern.add(new HourlyPatternResponse.Entry(hour, buckets[hour]));
        }
        return new HourlyPatternResponse(pattern);
    }
}
