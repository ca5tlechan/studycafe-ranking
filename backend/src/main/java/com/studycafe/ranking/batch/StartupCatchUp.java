package com.studycafe.ranking.batch;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 기동 시 놓친 04:00 자동 마감을 보정한다(§3.6a, Phase 12).
 * <p>무료 호스팅(Render Free)은 유휴 spin-down·재시작이 가능해, 04:00 정각에 인스턴스가 꺼져 있으면
 * {@code @Scheduled} 배치가 누락될 수 있다(UptimeRobot keep-alive 로 확률을 낮추지만 100% 보장은 아님).
 * 그 경우 다음 기동 시 {@link DailyCloseService#closeOverdue}(멱등: 밀린 세션을 각자의 04:00 경계로 마감)를
 * 돌려 누락 구간을 자가복구한다. 정상 가동 중 재시작에도 이미 마감된 세션은 건드리지 않는다.
 * <p><b>{@code @Async}</b>: {@code ApplicationReadyEvent} 리스너가 동기면 closeOverdue 가 끝날 때까지
 * readiness(헬스체크 통과)가 지연된다 — 백그라운드로 돌려 기동을 막지 않는다. 일시적 DB 오류에 대비해
 * 제한적 재시도(백오프)를 하고, 소진되면 로깅만 하고 다음 04:00 {@code @Scheduled} 배치에 맡긴다.
 * <p>사전 알림(03:30)은 시점이 지나면 무의미해(§8.4 베스트 에포트) catch-up 대상이 아니다. 페이지 단위
 * 분할 처리는 파일럿 규모(수십 명)에선 불필요해 도입하지 않는다.
 */
@Component
public class StartupCatchUp {

    private static final Logger log = LoggerFactory.getLogger(StartupCatchUp.class);

    private final DailyCloseService dailyCloseService;
    private final CatchUpStatus catchUpStatus;
    private final int maxAttempts;
    private final long backoffMs;

    // 단일 생성자 — Spring 이 이걸 쓰고, 재시도 파라미터는 @Value 기본값으로. 테스트는 명시값으로 호출한다.
    public StartupCatchUp(DailyCloseService dailyCloseService,
                          CatchUpStatus catchUpStatus,
                          @Value("${app.batch.startup-catchup.max-attempts:3}") int maxAttempts,
                          @Value("${app.batch.startup-catchup.backoff-ms:3000}") long backoffMs) {
        // 잘못된 설정으로 catch-up 이 조용히 무력화되지 않게 거부한다.
        // (maxAttempts<1 이면 한 번도 안 돌고, backoffMs<0 이면 지연 없이 재시도.)
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("app.batch.startup-catchup.max-attempts 는 1 이상이어야 합니다: " + maxAttempts);
        }
        if (backoffMs < 0) {
            throw new IllegalArgumentException("app.batch.startup-catchup.backoff-ms 는 음수일 수 없습니다: " + backoffMs);
        }
        this.dailyCloseService = dailyCloseService;
        this.catchUpStatus = catchUpStatus;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        long backoff = backoffMs;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                int closed = dailyCloseService.closeOverdue(Instant.now());
                if (closed > 0) {
                    log.info("기동 시 밀린 자동 마감 보정: {}건", closed);
                }
                catchUpStatus.markHealthy(); // 정상 처리 — degraded 였다면 해제
                return;
            } catch (RuntimeException e) {
                if (attempt >= maxAttempts) {
                    // 소진: 앱 기동은 막지 않되, 실패 상태를 노출한다(/healthz). 다음 성공한 catch-up 또는
                    // 정기 04:00 배치가 상태를 해제한다.
                    catchUpStatus.markFailed();
                    log.error("기동 시 자동 마감 보정 실패 — 재시도 {}회 소진, 다음 04:00 배치에 위임", maxAttempts, e);
                    return;
                }
                log.warn("기동 catch-up 실패(attempt {}/{}), {}ms 후 재시도: {}", attempt, maxAttempts, backoff, e.toString());
                if (!sleep(backoff)) {
                    return; // 인터럽트 시 조용히 종료
                }
                backoff *= 2;
            }
        }
    }

    private boolean sleep(long ms) {
        if (ms <= 0) {
            return true;
        }
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
