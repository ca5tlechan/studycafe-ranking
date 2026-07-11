package com.studycafe.ranking.session;

import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code isActiveSessionConflict} 판정 검증(H2).
 * 실 Postgres 부분 인덱스 충돌(true 케이스)은 로컬 검증(직접 INSERT → 유니크 위반)으로 확인했다.
 * 여기서는 더 위험한 경우 — "loginId 유니크 등 다른 제약 위반을 활성세션 충돌로 오판(false positive)하지 않는지" —
 * 를 실제 제약 위반 예외로 결정적으로 검증한다. (Phase 1의 isLoginIdConflict와 동일한 제약명 판정 패턴.)
 */
@SpringBootTest
@Transactional
class SessionConflictDetectionTest {

    @Autowired
    private SessionService sessionService;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("loginId 유니크 위반은 활성세션 충돌로 오판하지 않는다(false)")
    void doesNotMisclassifyOtherConstraintViolation() {
        userRepository.saveAndFlush(new User("dupuser", "{noop}pw", "김중복", 1, null));

        DataIntegrityViolationException ex = assertThrows(
                DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(new User("dupuser", "{noop}pw", "김중복", 1, null)));

        assertFalse(sessionService.isActiveSessionConflict(ex));
    }
}
