package com.studycafe.ranking.batch;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 04:00(KST) 자동 마감 배치(§3.6a). 로직은 {@link DailyCloseService} 가 갖고 여기선 트리거만 한다.
 * (무료 호스팅 슬립·콜드스타트로 실행이 누락될 수 있음 — §8.7. 그 경우에도 다음 실행이
 *  이전 날짜 경계 기준으로 밀린 세션을 마감하도록 서비스가 세션별 경계를 계산한다.)
 */
@Component
public class DailyCloseScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyCloseScheduler.class);

    private final DailyCloseService dailyCloseService;
    private final CatchUpStatus catchUpStatus;

    public DailyCloseScheduler(DailyCloseService dailyCloseService, CatchUpStatus catchUpStatus) {
        this.dailyCloseService = dailyCloseService;
        this.catchUpStatus = catchUpStatus;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            dailyCloseService.closeOverdue(Instant.now());
            catchUpStatus.markHealthy(); // 정기 배치 성공 — 기동 catch-up 실패로 남은 degraded 를 해제
        } catch (RuntimeException e) {
            // 스케줄 예외를 삼키면 조용히 멈춘다 — 다음 실행은 계속되도록 로깅만 하고 넘긴다.
            log.error("04:00 자동 마감 배치 실패", e);
        }
    }
}
