package com.studycafe.ranking.stats;

import com.studycafe.ranking.auth.JwtTokenProvider;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 마이페이지 통계 HTTP 계약: 인증(4개 엔드포인트)/정상/파라미터 검증. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MeStatsControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String bearer;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User("stats_mvc", "{noop}pw", "김엠통계", 1, null));
        bearer = "Bearer " + jwtTokenProvider.createToken(user.getId());
    }

    @Test
    @DisplayName("무토큰 → 401 (overview/calendar/weekday/hourly 전부)")
    void allEndpoints_requireAuth() throws Exception {
        mockMvc.perform(get("/api/me/stats/overview")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/stats/calendar").param("year", "2026").param("month", "7"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/stats/weekday-pattern")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/stats/hourly-pattern")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("overview 인증 → 200 (기록 없으면 0)")
    void overview_authenticated() throws Exception {
        mockMvc.perform(get("/api/me/stats/overview").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekSeconds").value(0))
                .andExpect(jsonPath("$.monthSeconds").value(0));
    }

    @Test
    @DisplayName("calendar 잘못된 month(13) → 400, ApiError 형식")
    void calendar_invalidMonth_returns400ApiError() throws Exception {
        mockMvc.perform(get("/api/me/stats/calendar")
                        .header("Authorization", bearer)
                        .param("year", "2026")
                        .param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("hourly-pattern → 24개 버킷")
    void hourlyPattern_returns24() throws Exception {
        mockMvc.perform(get("/api/me/stats/hourly-pattern").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pattern.length()").value(24));
    }
}
