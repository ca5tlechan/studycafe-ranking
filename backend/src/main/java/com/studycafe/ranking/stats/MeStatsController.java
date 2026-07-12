package com.studycafe.ranking.stats;

import com.studycafe.ranking.stats.dto.CalendarResponse;
import com.studycafe.ranking.stats.dto.HourlyPatternResponse;
import com.studycafe.ranking.stats.dto.OverviewResponse;
import com.studycafe.ranking.stats.dto.WeekdayPatternResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 마이페이지 통계(§5.1). 전부 인증 필요(현재 사용자 기준). */
@RestController
@RequestMapping("/api/me/stats")
public class MeStatsController {

    private final MeStatsService meStatsService;

    public MeStatsController(MeStatsService meStatsService) {
        this.meStatsService = meStatsService;
    }

    @GetMapping("/overview")
    public OverviewResponse overview(@AuthenticationPrincipal Long userId) {
        return meStatsService.overview(userId);
    }

    @GetMapping("/calendar")
    public CalendarResponse calendar(@AuthenticationPrincipal Long userId,
                                     @RequestParam @Min(2000) @Max(2100) int year,
                                     @RequestParam @Min(1) @Max(12) int month) {
        return meStatsService.calendar(userId, year, month);
    }

    @GetMapping("/weekday-pattern")
    public WeekdayPatternResponse weekdayPattern(@AuthenticationPrincipal Long userId) {
        return meStatsService.weekdayPattern(userId);
    }

    @GetMapping("/hourly-pattern")
    public HourlyPatternResponse hourlyPattern(@AuthenticationPrincipal Long userId) {
        return meStatsService.hourlyPattern(userId);
    }
}
