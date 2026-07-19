package com.studycafe.ranking.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VAPID 키 미설정 시 주입되는 no-op 전송기. 실제 전송 없이 로그만 남긴다.
 * 로컬/개발에서 03:30 배치의 대상 선정 로직을 그대로 태우되(§6 스케줄러), 전송만 생략한다.
 */
public class NoopWebPushSender implements WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(NoopWebPushSender.class);

    @Override
    public Result send(PushSubscription subscription, String payloadJson) {
        // endpoint 는 사용자별 capability URL 이라 로그에 남기지 않는다 — 진단엔 userId 로 충분.
        log.info("[push:noop] VAPID 미설정 — 전송 생략(userId={})", subscription.getUser().getId());
        return Result.OK;
    }
}
