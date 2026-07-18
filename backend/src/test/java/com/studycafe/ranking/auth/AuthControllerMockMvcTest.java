package com.studycafe.ranking.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 인증 HTTP 계약(이슈 #7): 쿠키 발급/본문 토큰 미포함/로그아웃 인증 필요. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerMockMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private User user;

    @BeforeEach
    void setUp() {
        // {noop} → DelegatingPasswordEncoder 가 평문 그대로 매칭
        user = userRepository.save(new User("auth_mvc", "{noop}pw", "김인증", 1, null));
    }

    @Test
    @DisplayName("로그인 성공 → HttpOnly scr_token 쿠키 발급, 본문엔 토큰 없음")
    void login_setsHttpOnlyCookie_noTokenInBody() throws Exception {
        // 컨트롤러가 ResponseCookie 를 Set-Cookie 헤더로 내려주므로 헤더로 계약을 고정한다.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"auth_mvc\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString(AuthCookieFactory.COOKIE_NAME + "=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.token").doesNotExist())      // 토큰은 본문에 싣지 않는다
                .andExpect(jsonPath("$.loginId").value("auth_mvc"));
    }

    @Test
    @DisplayName("로그인 실패(잘못된 비번) → 401, Set-Cookie 없음")
    void login_wrongPassword_401_noCookie() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"auth_mvc\",\"password\":\"WRONG\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("로그아웃은 인증 필요 — 쿠키 없이 호출하면 401 (강제 로그아웃 CSRF 차단)")
    void logout_withoutAuth_401() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃(인증됨) → 204, 쿠키 제거(Max-Age=0)")
    void logout_authenticated_clearsCookie() throws Exception {
        Cookie authCookie =
                new Cookie(AuthCookieFactory.COOKIE_NAME, jwtTokenProvider.createToken(user.getId()));
        mockMvc.perform(post("/api/auth/logout").cookie(authCookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString(AuthCookieFactory.COOKIE_NAME + "=;")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }
}
