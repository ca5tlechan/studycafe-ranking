package com.studycafe.ranking.batch;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 기동 시 놓친 04:00 자동 마감을 보정한다(§3.6a, Phase 12).
 * <p>무료 호스팅(Render Free)은 유휴 spin-down·재시작이 가능해, 04:00 정각에 인스턴스가 꺼져 있으면
 * {@code @Scheduled} 배치가 누락될 수 있다(UptimeRobot keep-alive 로 확률을 낮추지만 100% 보장은 아님).
 * 그 경우 다음 기동 시 여기서 {@link DailyCloseService#closeOverdue}(멱등: 밀린 세션을 각자의 04:00 경계로
 * 마감)를 한 번 돌려 누락 구간을 자가복구한다. 정상 가동 중 재시작에도 이미 마감된 세션은 건드리지 않는다.
 * <p>사전 알림(03:30)은 시점이 지나면 의미가 없어(§8.4 베스트 에포트) catch-up 대상이 아니다.
 */
@Component
public class StartupCatchUp {

    private static final Logger log = LoggerFactory.getLogger(StartupCatchUp.class);

    private final DailyCloseService dailyCloseService;

    public StartupCatchUp(DailyCloseService dailyCloseService) {
        this.dailyCloseService = dailyCloseService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        try {
            int closed = dailyCloseService.closeOverdue(Instant.now());
            if (closed > 0) {
                log.info("기동 시 밀린 자동 마감 보정: {}건", closed);
            }
        } catch (RuntimeException e) {
            // 기동 catch-up 실패가 앱 기동을 막지 않도록 삼키고 로깅만 — 다음 04:00 배치가 다시 시도한다.
            log.error("기동 시 자동 마감 보정 실패", e);
        }
    }
}
