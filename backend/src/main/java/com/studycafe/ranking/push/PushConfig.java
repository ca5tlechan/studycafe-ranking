package com.studycafe.ranking.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * VAPID 키 유무에 따라 실제 전송기/no-op 전송기를 선택 주입한다.
 * 키가 없으면 앱은 정상 기동하되 03:30 배치는 전송을 생략한다(로컬/개발에서도 부팅 가능).
 */
@Configuration
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    @Bean
    public WebPushSender webPushSender(WebPushProperties props) {
        if (props.isEnabled()) {
            log.info("[push] VAPID 활성 — Web Push 전송 사용");
            return new JdkWebPushSender(props.getPublicKey(), props.getPrivateKey(), props.getSubject());
        }
        log.warn("[push] VAPID 키 미설정 — Web Push 비활성(no-op). 03:30 배치는 대상만 선정하고 전송은 생략한다.");
        return new NoopWebPushSender();
    }
}
