package com.studycafe.ranking.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 03:30(KST) 사전 알림 배치(§3.6b). 시각은 app.push.pre-close-cron 으로 튜닝 가능(§9.4, 03:00~03:30).
 * 로직은 {@link PreCloseNotifyService} 가 갖고 여기선 트리거만 한다.
 * (VAPID 미설정이면 전송은 no-op — 대상 선정만 돌고 로그만 남는다.)
 */
@Component
public class PreCloseNotifyScheduler {

    private static final Logger log = LoggerFactory.getLogger(PreCloseNotifyScheduler.class);

    private final PreCloseNotifyService preCloseNotifyService;

    public PreCloseNotifyScheduler(PreCloseNotifyService preCloseNotifyService) {
        this.preCloseNotifyService = preCloseNotifyService;
    }

    @Scheduled(cron = "${app.push.pre-close-cron:0 30 3 * * *}", zone = "Asia/Seoul")
    public void run() {
        try {
            int attempted = preCloseNotifyService.notifyActiveUsers();
            log.info("03:30 사전 알림 발송 시도 {}건", attempted);
        } catch (RuntimeException e) {
            // 스케줄 예외를 삼키면 조용히 멈춘다 — 로깅만 하고 다음 실행은 계속되게 한다.
            log.error("03:30 사전 알림 배치 실패", e);
        }
    }
}
