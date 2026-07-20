package com.studycafe.ranking.system;

import com.studycafe.ranking.batch.CatchUpStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * keep-alive 핑 대상(§Phase 12). UptimeRobot 등 외부 모니터가 14분마다 여기를 찔러 인스턴스를 깨워두면,
 * 무료 호스팅(Render)의 유휴 spin-down 을 피해 04:00/03:30 @Scheduled 배치가 정시에 뜬다. 공개 엔드포인트.
 * <p>자동 마감 catch-up 이 실패해 degraded 상태면 본문에 노출한다(HTTP 는 <b>항상 200</b> — 라이브니스와
 * keep-alive 를 깨지 않기 위함). 운영자/모니터는 본문 {@code status} 로 배치 실패를 인지할 수 있다.
 */
@RestController
public class HealthController {

    private final CatchUpStatus catchUpStatus;

    public HealthController(CatchUpStatus catchUpStatus) {
        this.catchUpStatus = catchUpStatus;
    }

    @GetMapping("/healthz")
    public Map<String, Object> healthz() {
        Map<String, Object> body = new LinkedHashMap<>();
        if (catchUpStatus.isDegraded()) {
            body.put("status", "degraded");
            body.put("batchCatchUpFailingSince", String.valueOf(catchUpStatus.getDegradedSince()));
        } else {
            body.put("status", "ok");
        }
        return body;
    }
}
