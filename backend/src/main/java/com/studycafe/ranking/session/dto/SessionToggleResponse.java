package com.studycafe.ranking.session.dto;

import com.studycafe.ranking.domain.CheckInSession;

import java.time.Instant;

/** 토글 결과. action = CHECK_IN(체크인함) | CHECK_OUT(체크아웃함). */
public record SessionToggleResponse(
        String action,
        Long sessionId,
        String status,
        Instant checkInAt,
        Instant checkOutAt
) {
    public static SessionToggleResponse checkedIn(CheckInSession s) {
        return new SessionToggleResponse("CHECK_IN", s.getId(), s.getStatus().name(),
                s.getCheckInAt(), s.getCheckOutAt());
    }

    public static SessionToggleResponse checkedOut(CheckInSession s) {
        return new SessionToggleResponse("CHECK_OUT", s.getId(), s.getStatus().name(),
                s.getCheckInAt(), s.getCheckOutAt());
    }
}
