package com.studycafe.ranking.batch;

import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 자동 마감 배치의 운영 상태(§3.6a, Phase 12). 기동 catch-up 이 재시도를 소진해 실패하면 degraded 로
 * 표시하고, 이후 성공한 catch-up 또는 정기 04:00 배치가 상태를 해제한다. {@code /healthz} 응답 본문에
 * 노출돼(HTTP 200 은 유지 — 라이브니스/keep-alive 불변) 운영자·UptimeRobot 이 배치 실패를 인지할 수 있다.
 */
@Component
public class CatchUpStatus {

    private volatile boolean degraded = false;
    private volatile Instant degradedSince;

    /** 자동 마감이 정상 처리됨 — degraded 해제. */
    public void markHealthy() {
        degraded = false;
        degradedSince = null;
    }

    /** 기동 catch-up 재시도 소진 — degraded 진입(최초 시각 기록). */
    public void markFailed() {
        if (!degraded) {
            degradedSince = Instant.now();
            degraded = true;
        }
    }

    public boolean isDegraded() {
        return degraded;
    }

    public Instant getDegradedSince() {
        return degradedSince;
    }
}
