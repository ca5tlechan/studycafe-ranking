package com.studycafe.ranking.session;

import com.studycafe.ranking.common.exception.InvalidCafeTokenException;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.CafeRepository;
import com.studycafe.ranking.repository.UserRepository;
import com.studycafe.ranking.session.dto.CurrentSessionResponse;
import com.studycafe.ranking.session.dto.SessionToggleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class SessionServiceTest {

    @Autowired
    private SessionService sessionService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CafeRepository cafeRepository;

    private Long userId;
    private String cafeToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User("sess_tester", "{noop}pw", "김세션", 1, null));
        userId = user.getId();
        cafeToken = cafeRepository.findAll().get(0).getQrToken(); // 시드된 파일럿 카페
    }

    @Test
    @DisplayName("첫 토글 → 체크인(ACTIVE), current 활성")
    void toggle_firstTime_checksIn() {
        SessionToggleResponse res = sessionService.toggle(userId, cafeToken);
        assertEquals("CHECK_IN", res.action());
        assertEquals("ACTIVE", res.status());
        assertNull(res.checkOutAt());

        CurrentSessionResponse current = sessionService.current(userId);
        assertTrue(current.active());
        assertNotNull(current.checkInAt());
    }

    @Test
    @DisplayName("두 번째 토글 → 체크아웃(COMPLETED), current 비활성")
    void toggle_secondTime_checksOut() {
        sessionService.toggle(userId, cafeToken); // 체크인
        SessionToggleResponse res = sessionService.toggle(userId, cafeToken); // 체크아웃
        assertEquals("CHECK_OUT", res.action());
        assertEquals("COMPLETED", res.status());
        assertNotNull(res.checkOutAt());

        assertFalse(sessionService.current(userId).active());
    }

    @Test
    @DisplayName("활성 세션 없으면 current 비활성")
    void current_noActive_isInactive() {
        assertFalse(sessionService.current(userId).active());
    }

    @Test
    @DisplayName("잘못된 카페 토큰 → 예외")
    void toggle_invalidToken_throws() {
        assertThrows(InvalidCafeTokenException.class,
                () -> sessionService.toggle(userId, "NO_SUCH_TOKEN"));
    }
}
