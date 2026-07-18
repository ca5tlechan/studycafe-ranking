package com.studycafe.ranking.session;

import com.studycafe.ranking.auth.AuthCookieFactory;
import com.studycafe.ranking.auth.JwtTokenProvider;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP 계약 검증: 인증/라우팅/@Valid 400/토큰 404/정상 200. (H2, 시드된 파일럿 카페 사용) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionControllerMockMvcTest {

    private static final String PILOT_CAFE_BODY = "{\"cafeToken\":\"STUDYCAFE-PILOT-001\"}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Cookie authCookie;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User("mvc_tester", "{noop}pw", "김엠", 1, null));
        authCookie = new Cookie(AuthCookieFactory.COOKIE_NAME, jwtTokenProvider.createToken(user.getId()));
    }

    @Test
    @DisplayName("/current 무토큰 → 401")
    void current_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/sessions/current"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("쿠키 없이 Authorization: Bearer 만 → 401 (구 헤더 인증 재도입 감지)")
    void bearerHeaderWithoutCookie_rejected() throws Exception {
        mockMvc.perform(get("/api/sessions/current")
                        .header("Authorization", "Bearer " + authCookie.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/current 인증 → 200 active:false")
    void current_authenticated_returnsInactive() throws Exception {
        mockMvc.perform(get("/api/sessions/current").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("toggle 빈 cafeToken → 400(@Valid)")
    void toggle_blankCafeToken_returns400() throws Exception {
        mockMvc.perform(post("/api/sessions/toggle")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cafeToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("toggle 잘못된 토큰 → 404")
    void toggle_invalidCafeToken_returns404() throws Exception {
        mockMvc.perform(post("/api/sessions/toggle")
                        .cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cafeToken\":\"NO_SUCH_TOKEN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("toggle 두 번 → CHECK_IN 후 CHECK_OUT")
    void toggle_checksInThenOut() throws Exception {
        mockMvc.perform(post("/api/sessions/toggle").cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON).content(PILOT_CAFE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("CHECK_IN"));

        mockMvc.perform(post("/api/sessions/toggle").cookie(authCookie)
                        .contentType(MediaType.APPLICATION_JSON).content(PILOT_CAFE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("CHECK_OUT"));
    }
}
