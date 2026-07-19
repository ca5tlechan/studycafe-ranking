package com.studycafe.ranking.push;

/**
 * 한 구독으로 실제 푸시를 전송하는 전략. VAPID 키가 설정되면 {@link JdkWebPushSender},
 * 없으면 {@link NoopWebPushSender} 가 주입된다(§8.4 — 도달은 베스트 에포트).
 */
public interface WebPushSender {

    Result send(PushSubscription subscription, String payloadJson);

    enum Result {
        /** 2xx — 전송 수락됨. */
        OK,
        /** 404/410 — 구독이 만료/해지됨. 저장소에서 제거해야 함. */
        GONE,
        /** 그 외 오류(네트워크/4xx/5xx) — 이번엔 건너뜀, 구독은 유지. */
        FAILED
    }
}
