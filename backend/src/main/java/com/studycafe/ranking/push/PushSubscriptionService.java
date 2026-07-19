package com.studycafe.ranking.push;

import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.push.dto.PushDtos.SubscribeRequest;
import com.studycafe.ranking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.List;

/**
 * 구독 저장/해지 + 대상 유저들에게 푸시 발송(§3.6b, §6). 발송은 네트워크 I/O 이므로 DB 트랜잭션
 * 밖에서 돌리고, 만료된 구독(410/404)은 즉시 정리한다.
 */
@Service
public class PushSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(PushSubscriptionService.class);

    private final PushSubscriptionRepository repository;
    private final UserRepository userRepository;
    private final WebPushSender sender;
    private final ObjectMapper objectMapper;

    public PushSubscriptionService(PushSubscriptionRepository repository,
                                   UserRepository userRepository,
                                   WebPushSender sender,
                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.sender = sender;
        this.objectMapper = objectMapper;
    }

    /** 구독 저장. 같은 endpoint 가 이미 있으면 소유자/키를 갱신(재로그인·재구독). */
    @Transactional
    public void subscribe(Long userId, SubscribeRequest req) {
        User user = userRepository.getReferenceById(userId);
        repository.findByEndpoint(req.endpoint()).ifPresentOrElse(
                existing -> existing.refresh(user, req.keys().p256dh(), req.keys().auth()),
                () -> repository.save(new PushSubscription(
                        user, req.endpoint(), req.keys().p256dh(), req.keys().auth())));
    }

    /** 구독 해지(알림 끄기). endpoint 가 없어도 멱등. */
    @Transactional
    public void unsubscribe(String endpoint) {
        repository.deleteByEndpoint(endpoint);
    }

    /**
     * 주어진 유저들의 모든 구독에 같은 메시지를 발송한다. 트랜잭션을 걸지 않는다 — 발송 루프가
     * 네트워크 I/O 라 커넥션을 오래 잡으면 안 되기 때문. 만료 구독은 건별로 제거한다.
     *
     * @return 발송을 시도한 구독 수
     */
    public int sendToUsers(Collection<Long> userIds, PushMessage message) {
        if (userIds.isEmpty()) {
            return 0;
        }
        List<PushSubscription> subs = repository.findByUserIdIn(userIds);
        if (subs.isEmpty()) {
            return 0;
        }
        String payload = objectMapper.writeValueAsString(message);
        int attempted = 0;
        for (PushSubscription sub : subs) {
            WebPushSender.Result result = sender.send(sub, payload);
            attempted++;
            if (result == WebPushSender.Result.GONE) {
                repository.deleteByEndpoint(sub.getEndpoint());
                log.info("[push] 만료 구독 제거 endpoint 앞부분={}",
                        sub.getEndpoint().length() <= 40 ? sub.getEndpoint() : sub.getEndpoint().substring(0, 40) + "…");
            }
        }
        return attempted;
    }
}
