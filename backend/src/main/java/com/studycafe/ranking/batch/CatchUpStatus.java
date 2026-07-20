package com.studycafe.ranking.batch;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 자동 마감 배치의 운영 상태(§3.6a, Phase 12). 기동 catch-up 이 재시도를 소진해 실패하면 degraded 로
 * 표시하고, 이후 성공한 catch-up 또는 정기 04:00 배치가 상태를 해제한다. {@code /healthz} 응답 본문에
 * 노출돼(HTTP 200 은 유지 — 라이브니스/keep-alive 불변) 운영자·UptimeRobot 이 배치 실패를 인지할 수 있다.
 * <p>degraded 여부와 진입 시각은 하나의 {@link Snapshot} 으로 원자적으로 교체/조회한다 — 따로 읽으면
 * markHealthy 가 끼어 "degraded 인데 시각 null" 같은 불일치가 날 수 있다.
 */
@Component
public class CatchUpStatus {

    /** degraded 여부와 진입 시각을 함께 담는 불변 스냅샷. degraded 면 시각이 반드시 있어야 한다. */
    public record Snapshot(boolean degraded, Instant degradedSince) {
        public Snapshot {
            if (degraded && degradedSince == null) {
                throw new IllegalArgumentException("degraded 스냅샷은 degradedSince 가 있어야 합니다");
            }
        }
    }

    private static final Snapshot HEALTHY = new Snapshot(false, null);

    private final AtomicReference<Snapshot> state = new AtomicReference<>(HEALTHY);

    /** 자동 마감이 정상 처리됨 — degraded 해제. */
    public void markHealthy() {
        state.set(HEALTHY);
    }

    /** 기동 catch-up 재시도 소진 — degraded 진입(최초 시각 유지). */
    public void markFailed() {
        state.updateAndGet(current -> current.degraded() ? current : new Snapshot(true, Instant.now()));
    }

    /** degraded 여부와 시각을 일관되게 한 번에 읽는다. */
    public Snapshot snapshot() {
        return state.get();
    }

    public boolean isDegraded() {
        return state.get().degraded();
    }
}
