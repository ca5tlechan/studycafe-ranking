package com.studycafe.ranking.push;

import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.push.dto.PushDtos.SubscribeRequest;
import com.studycafe.ranking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 구독 저장/해지 + 대상 유저들에게 푸시 발송(§3.6b, §6). 발송은 네트워크 I/O 이므로 DB 트랜잭션
 * 밖에서 <b>제한된 동시성 + 전체 데드라인</b>으로 돌려, 느린/죽은 endpoint 몇 개가 03:30 배치 전체를
 * 04:00 마감 창 밖으로 밀지 않게 한다. 만료된 구독(410/404)은 즉시 정리한다.
 */
@Service
public class PushSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(PushSubscriptionService.class);

    /** 03:30 배치 전체 발송의 상한. 개별 요청 타임아웃(JdkWebPushSender, 10s)과 별개의 배치 데드라인. */
    private static final Duration BATCH_DEADLINE = Duration.ofMinutes(2);
    /** 동시 전송 상한. 파일럿 규모에 맞춘 작은 풀 — 느린 endpoint 병렬화 + 자원 과점 방지. */
    private static final int MAX_CONCURRENCY = 8;

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

    /**
     * 구독 저장. endpoint 를 검증(https·공인 호스트)한 뒤, 같은 endpoint 가
     * <b>현재 사용자 소유</b>면 키를 갱신하고, 다른 사용자 소유면(같은 브라우저 재로그인) 그 행을 지우고
     * 현재 사용자로 새로 만든다 — 남의 구독의 소유자/키를 그 자리에서 바꾸지 않는다(§6).
     */
    @Transactional
    public void subscribe(Long userId, SubscribeRequest req) {
        WebPushEndpointValidator.validate(req.endpoint());
        User user = userRepository.getReferenceById(userId);
        String p256dh = req.keys().p256dh();
        String auth = req.keys().auth();

        repository.findByEndpoint(req.endpoint()).ifPresentOrElse(existing -> {
            if (existing.getUser().getId().equals(userId)) {
                existing.refresh(user, p256dh, auth); // 본인 재구독 — 키 갱신
            } else {
                // 이 브라우저 채널을 다른 계정이 물고 있던 상태(공용 기기 재로그인). 남의 행을 갱신하지 않고
                // 지운 뒤 현재 사용자로 새로 만든다. delete 를 먼저 flush 하지 않으면 Hibernate 가 한 트랜잭션
                // 안에서 insert 를 delete 보다 먼저 실행해, 같은 endpoint 유니크 제약을 위반한다.
                repository.delete(existing);
                repository.flush();
                repository.save(new PushSubscription(user, req.endpoint(), p256dh, auth));
            }
        }, () -> repository.save(new PushSubscription(user, req.endpoint(), p256dh, auth)));
    }

    /** 본인 구독 해지(알림 끄기). 소유자 범위로만 삭제 — 남의 endpoint 는 지우지 못한다. 없어도 멱등. */
    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        repository.deleteByUserIdAndEndpoint(userId, endpoint);
    }

    /**
     * 주어진 유저들의 모든 구독에 같은 메시지를 발송한다. 트랜잭션을 걸지 않는다(네트워크 I/O). 발송은
     * 제한된 동시성으로 병렬 처리하고 전체 데드라인을 둔다 — 데드라인을 넘긴 요청은 실패로 접는다.
     * 만료(410/404) 구독은 건별로 제거한다.
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

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(subs.size(), MAX_CONCURRENCY));
        List<String> goneEndpoints = new ArrayList<>();
        try {
            List<Future<WebPushSender.Result>> futures = new ArrayList<>(subs.size());
            for (PushSubscription sub : subs) {
                futures.add(pool.submit(() -> sender.send(sub, payload)));
            }
            long deadlineNanos = System.nanoTime() + BATCH_DEADLINE.toNanos();
            for (int i = 0; i < subs.size(); i++) {
                WebPushSender.Result result = awaitWithinDeadline(futures.get(i), deadlineNanos);
                if (result == WebPushSender.Result.GONE) {
                    goneEndpoints.add(subs.get(i).getEndpoint());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        // 만료 구독 정리. Spring Data 파생 delete 는 자체 트랜잭션에서 실행된다.
        // endpoint 값은 로그에 남기지 않는다(사용자별 capability URL).
        for (String endpoint : goneEndpoints) {
            repository.deleteByEndpoint(endpoint);
        }
        if (!goneEndpoints.isEmpty()) {
            log.info("[push] 만료 구독 {}건 제거", goneEndpoints.size());
        }
        return subs.size();
    }

    /** 남은 데드라인만큼만 결과를 기다린다. 초과/오류/취소는 FAILED(구독 유지). */
    private WebPushSender.Result awaitWithinDeadline(Future<WebPushSender.Result> future, long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) {
            future.cancel(true);
            return WebPushSender.Result.FAILED;
        }
        try {
            return future.get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return WebPushSender.Result.FAILED;
        } catch (ExecutionException e) {
            log.warn("[push] 전송 작업 실패", e.getCause());
            return WebPushSender.Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return WebPushSender.Result.FAILED;
        }
    }
}
