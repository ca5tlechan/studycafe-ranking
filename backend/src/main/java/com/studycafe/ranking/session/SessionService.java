package com.studycafe.ranking.session;

import com.studycafe.ranking.common.exception.AlreadyCheckedInException;
import com.studycafe.ranking.common.exception.InvalidCafeTokenException;
import com.studycafe.ranking.domain.Cafe;
import com.studycafe.ranking.domain.CheckInSession;
import com.studycafe.ranking.domain.SessionStatus;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.CafeRepository;
import com.studycafe.ranking.repository.CheckInSessionRepository;
import com.studycafe.ranking.repository.UserRepository;
import com.studycafe.ranking.session.dto.CurrentSessionResponse;
import com.studycafe.ranking.session.dto.SessionToggleResponse;
import java.util.Locale;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 체크인/체크아웃 원본 세션 기록(Phase 2). 04:00 분할·집계는 Phase 3.
 * 유저당 ACTIVE 세션 1개는 (1) 토글 트랜잭션의 앱 레벨 검사 + (2) Postgres 부분 유니크 인덱스로 보장.
 */
@Service
public class SessionService {

    private final CafeRepository cafeRepository;
    private final CheckInSessionRepository sessionRepository;
    private final UserRepository userRepository;

    public SessionService(CafeRepository cafeRepository,
                          CheckInSessionRepository sessionRepository,
                          UserRepository userRepository) {
        this.cafeRepository = cafeRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public SessionToggleResponse toggle(Long userId, String cafeToken) {
        Cafe cafe = cafeRepository.findByQrToken(cafeToken)
                .orElseThrow(() -> new InvalidCafeTokenException(cafeToken));

        Optional<CheckInSession> active = sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE);
        if (active.isPresent()) {
            CheckInSession session = active.get();
            session.checkOut(Instant.now());
            return SessionToggleResponse.checkedOut(session);
        }

        User user = userRepository.getReferenceById(userId);
        CheckInSession session = new CheckInSession(user, cafe, Instant.now());
        try {
            sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            // 동시 더블탭으로 활성 세션 부분 유니크 인덱스 충돌만 409로. 그 외 제약 위반은 전파.
            if (isActiveSessionConflict(e)) {
                throw new AlreadyCheckedInException();
            }
            throw e;
        }
        return SessionToggleResponse.checkedIn(session);
    }

    @Transactional(readOnly = true)
    public CurrentSessionResponse current(Long userId) {
        return sessionRepository.findByUserIdAndStatusWithCafe(userId, SessionStatus.ACTIVE)
                .map(CurrentSessionResponse::of)
                .orElseGet(CurrentSessionResponse::none);
    }

    private boolean isActiveSessionConflict(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                if (name != null && name.toLowerCase(Locale.ROOT).contains("ux_active_session_per_user")) {
                    return true;
                }
            }
        }
        return false;
    }
}
