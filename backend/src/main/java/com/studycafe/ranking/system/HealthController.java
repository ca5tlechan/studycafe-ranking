package com.studycafe.ranking.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * keep-alive 핑 대상(§Phase 12). UptimeRobot 등 외부 모니터가 14분마다 여기를 찔러 인스턴스를 깨워두면,
 * 무료 호스팅(Render)의 유휴 spin-down 을 피해 04:00/03:30 @Scheduled 배치가 정시에 뜬다. 공개 엔드포인트.
 */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public String healthz() {
        return "ok";
    }
}
