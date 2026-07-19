package com.studycafe.ranking.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    /** 재구독 upsert 판정용. */
    Optional<PushSubscription> findByEndpoint(String endpoint);

    /** 03:30 사전 알림 전송 대상 — ACTIVE 유저들의 구독 전부. user 를 fetch(발송엔 불필요하나 로깅/일관성). */
    @Query("select s from PushSubscription s where s.user.id in :userIds")
    List<PushSubscription> findByUserIdIn(@Param("userIds") Collection<Long> userIds);

    /** 죽은 구독(404/410) 정리 — 푸시 서비스가 만료를 확정한 endpoint 라 소유자 무관하게 제거. */
    void deleteByEndpoint(String endpoint);

    /** 사용자 본인 구독 해지 — 소유자 범위로 제한해 남의 구독을 못 지우게 한다(§6). */
    void deleteByUserIdAndEndpoint(Long userId, String endpoint);

    /** 사용자 삭제 시 연쇄 정리(FK). */
    void deleteByUserId(Long userId);
}
