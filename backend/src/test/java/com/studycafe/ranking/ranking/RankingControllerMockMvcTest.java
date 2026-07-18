package com.studycafe.ranking.ranking;

import com.studycafe.ranking.auth.AuthCookieFactory;
import com.studycafe.ranking.auth.JwtTokenProvider;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.UserRepository;
import jakarta.servlet.http.Cookie;
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

/** 랭킹 HTTP 계약: 인증(3개)/정상/period 검증/무소속 우리학교. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RankingControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Cookie authCookie;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User("rank_mvc", "{noop}pw", "김랭", 1, null));
        authCookie = new Cookie(AuthCookieFactory.COOKIE_NAME, jwtTokenProvider.createToken(user.getId()));
    }

    @Test
    @DisplayName("무토큰 → 401 (individual/school/school/mine)")
    void endpoints_requireAuth() throws Exception {
        mockMvc.perform(get("/api/rankings/individual")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/rankings/school")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/rankings/school/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("individual 인증 → 200, period 기본 this_week")
    void individual_ok_defaultPeriod() throws Exception {
        mockMvc.perform(get("/api/rankings/individual").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("this_week"));
    }

    @Test
    @DisplayName("잘못된 period → 400")
    void invalidPeriod_returns400() throws Exception {
        mockMvc.perform(get("/api/rankings/individual")
                        .cookie(authCookie)
                        .param("period", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("우리학교: 무소속 유저 → available=false")
    void schoolMine_noSchool_unavailable() throws Exception {
        mockMvc.perform(get("/api/rankings/school/mine").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }
}
