package com.studycafe.ranking.session.dto;

import com.studycafe.ranking.domain.CheckInSession;

import java.time.Instant;

/** 현재 활성 세션. active=false면 나머지는 null. */
public record CurrentSessionResponse(
        boolean active,
        Long sessionId,
        Instant checkInAt,
        String cafeName
) {
    public static CurrentSessionResponse of(CheckInSession s) {
        return new CurrentSessionResponse(true, s.getId(), s.getCheckInAt(), s.getCafe().getName());
    }

    public static CurrentSessionResponse none() {
        return new CurrentSessionResponse(false, null, null, null);
    }
}
