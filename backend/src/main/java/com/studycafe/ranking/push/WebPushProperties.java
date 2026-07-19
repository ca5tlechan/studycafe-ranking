package com.studycafe.ranking.push;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * VAPID 설정. 키는 비밀정보이므로 환경변수로만 주입하고 커밋하지 않는다(§7.78).
 * 공개키/개인키가 모두 있어야 Web Push 가 활성화된다. 없으면 no-op(§8.4).
 */
@Component
public class WebPushProperties {

    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public WebPushProperties(
            @Value("${app.push.vapid.public-key:}") String publicKey,
            @Value("${app.push.vapid.private-key:}") String privateKey,
            @Value("${app.push.vapid.subject:mailto:admin@studycafe.app}") String subject) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.subject = subject;
    }

    /** 두 키가 모두 설정됐을 때만 활성. */
    public boolean isEnabled() {
        return !publicKey.isBlank() && !privateKey.isBlank();
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public String getSubject() {
        return subject;
    }
}
