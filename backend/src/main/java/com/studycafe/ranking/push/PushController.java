package com.studycafe.ranking.push;

import com.studycafe.ranking.push.dto.PushDtos.SubscribeRequest;
import com.studycafe.ranking.push.dto.PushDtos.TestPushResponse;
import com.studycafe.ranking.push.dto.PushDtos.UnsubscribeRequest;
import com.studycafe.ranking.push.dto.PushDtos.VapidKeyResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web Push 구독 API(§6). 모든 엔드포인트는 인증 필요(SecurityConfig 의 anyRequest().authenticated()).
 */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushSubscriptionService pushService;
    private final WebPushProperties props;

    public PushController(PushSubscriptionService pushService, WebPushProperties props) {
        this.pushService = pushService;
        this.props = props;
    }

    /** 프론트가 구독 전 VAPID 공개키를 받는다. 비활성이면 publicKey=null → 프론트가 토글을 숨긴다. */
    @GetMapping("/vapid-public-key")
    public VapidKeyResponse vapidPublicKey() {
        boolean enabled = props.isEnabled();
        return new VapidKeyResponse(enabled, enabled ? props.getPublicKey() : null);
    }

    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@AuthenticationPrincipal Long userId,
                          @Valid @RequestBody SubscribeRequest request) {
        pushService.subscribe(userId, request);
    }

    @PostMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@AuthenticationPrincipal Long userId,
                            @Valid @RequestBody UnsubscribeRequest request) {
        pushService.unsubscribe(userId, request.endpoint());
    }

    /**
     * 본인에게 즉시 테스트 알림을 보낸다 — 03:30 배치를 기다리지 않고 구독→전송→SW→표시 파이프라인을
     * 확인하는 용도. 본인 구독으로만 발송하므로 남용 위험이 없다. sent=0 이면 이 기기에 저장된 구독 없음.
     */
    @PostMapping("/test")
    public TestPushResponse test(@AuthenticationPrincipal Long userId) {
        int sent = pushService.sendToUsers(
                List.of(userId),
                new PushMessage("테스트 알림 📚", "알림이 정상 작동해요! 03:30 마감 알림도 이렇게 도착해요.", "/my"));
        return new TestPushResponse(sent);
    }
}
