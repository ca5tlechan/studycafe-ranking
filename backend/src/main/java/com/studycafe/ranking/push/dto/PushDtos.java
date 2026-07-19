package com.studycafe.ranking.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Web Push 관련 요청/응답 DTO. 요청 본문은 브라우저 {@code PushSubscription.toJSON()} 형태와 1:1.
 */
public final class PushDtos {

    private PushDtos() {
    }

    /** {@code { endpoint, keys: { p256dh, auth } }} — 브라우저 구독 객체 그대로. */
    public record SubscribeRequest(
            @NotBlank String endpoint,
            @NotNull @Valid Keys keys) {

        public record Keys(
                @NotBlank String p256dh,
                @NotBlank String auth) {
        }
    }

    /** 구독 해지(알림 끄기). */
    public record UnsubscribeRequest(@NotBlank String endpoint) {
    }

    /** 프론트가 구독 전 받는 응답. enabled=false 면 토글을 숨긴다. */
    public record VapidKeyResponse(boolean enabled, String publicKey) {
    }
}
