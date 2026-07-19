package com.studycafe.ranking.batch;

import com.studycafe.ranking.domain.SessionStatus;
import com.studycafe.ranking.push.PushMessage;
import com.studycafe.ranking.push.PushSubscriptionService;
import com.studycafe.ranking.repository.CheckInSessionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 03:30 사전 알림(§3.6b)의 대상 선정 + 발송 로직. 스케줄러({@link PreCloseNotifyScheduler})는
 * 트리거만 하고 실제 로직은 여기 둔다(테스트 용이). 그 시점 ACTIVE 유저에게 "곧 04:00 마감" 공지.
 */
@Service
public class PreCloseNotifyService {

    /** §3.6b 안내 문구. 도달은 베스트 에포트라 페널티는 이와 무관하게 세션 상태로만 판정(§8.4). */
    static final PushMessage MESSAGE = new PushMessage(
            "곧 새벽 4시에 하루가 마감돼요",
            "계속 공부하려면 04:00 이후 다시 체크인하세요. 체크아웃/재체크인 없이 넘기면 자동 종료되고 경고가 쌓여요.",
            "/checkin");

    private final CheckInSessionRepository sessionRepository;
    private final PushSubscriptionService pushSubscriptionService;

    public PreCloseNotifyService(CheckInSessionRepository sessionRepository,
                                 PushSubscriptionService pushSubscriptionService) {
        this.sessionRepository = sessionRepository;
        this.pushSubscriptionService = pushSubscriptionService;
    }

    /**
     * 지금 ACTIVE 인 유저 전부에게 사전 알림을 보낸다.
     *
     * @return 발송을 시도한 구독 수(대상 유저 수가 아님 — 유저당 여러 기기 가능)
     */
    public int notifyActiveUsers() {
        List<Long> activeUserIds = sessionRepository.findUserIdsByStatus(SessionStatus.ACTIVE);
        return pushSubscriptionService.sendToUsers(activeUserIds, MESSAGE);
    }
}
