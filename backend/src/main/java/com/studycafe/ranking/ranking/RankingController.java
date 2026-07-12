package com.studycafe.ranking.ranking;

import com.studycafe.ranking.ranking.dto.IndividualRankingResponse;
import com.studycafe.ranking.ranking.dto.SchoolMineResponse;
import com.studycafe.ranking.ranking.dto.SchoolRankingResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 랭킹(§5.2/§5.3). 전부 인증 필요. period 기본값 this_week, 잘못된 값이면 400. */
@RestController
@RequestMapping("/api/rankings")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/individual")
    public IndividualRankingResponse individual(@AuthenticationPrincipal Long userId,
                                                @RequestParam(defaultValue = "this_week") RankingPeriod period) {
        return rankingService.individual(userId, period);
    }

    @GetMapping("/school")
    public SchoolRankingResponse school(@RequestParam(defaultValue = "this_week") RankingPeriod period) {
        return rankingService.school(period);
    }

    @GetMapping("/school/mine")
    public SchoolMineResponse schoolMine(@AuthenticationPrincipal Long userId,
                                         @RequestParam(defaultValue = "this_week") RankingPeriod period) {
        return rankingService.schoolMine(userId, period);
    }
}
