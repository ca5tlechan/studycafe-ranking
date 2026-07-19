package com.studycafe.ranking.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studycafe.ranking.auth.AuthCookieFactory;
import com.studycafe.ranking.auth.JwtTokenProvider;
import com.studycafe.ranking.domain.Cafe;
import com.studycafe.ranking.domain.CheckInSession;
import com.studycafe.ranking.domain.Role;
import com.studycafe.ranking.domain.School;
import com.studycafe.ranking.domain.SessionStatus;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.CafeRepository;
import com.studycafe.ranking.repository.CheckInSessionRepository;
import com.studycafe.ranking.repository.SchoolRepository;
import com.studycafe.ranking.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 API — 권한 격리 + 핵심 조작. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminControllerMockMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private CafeRepository cafeRepository;
    @Autowired private CheckInSessionRepository sessionRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private User admin;
    private User normal;
    private Cookie adminCookie;
    private Cookie userCookie;

    @BeforeEach
    void setUp() {
        admin = new User("admin_mvc", "{noop}pw", "관리자", 1, null);
        admin.changeRole(Role.ADMIN);
        admin = userRepository.save(admin);
        normal = userRepository.save(new User("user_mvc", "{noop}pw", "일반유저", 1, null));
        adminCookie = cookie(admin);
        userCookie = cookie(normal);
    }

    private Cookie cookie(User u) {
        return new Cookie(AuthCookieFactory.COOKIE_NAME, jwtTokenProvider.createToken(u.getId()));
    }

    // ----- 권한 격리 -----

    @Test
    @DisplayName("무인증 → 401, 일반 유저 → 403, 관리자 → 200")
    void authorization() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/users").cookie(userCookie)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").cookie(adminCookie)).andExpect(status().isOk());
    }

    // ----- role -----

    @Test
    @DisplayName("일반 유저에게 ADMIN 부여 → 204, 목록에 role=ADMIN")
    void grantAdmin() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + normal.getId() + "/role")
                        .cookie(adminCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(
                userRepository.findById(normal.getId()).orElseThrow().isAdmin());
    }

    @Test
    @DisplayName("본인 강등 금지 → 400")
    void cannotDemoteSelf() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + admin.getId() + "/role")
                        .cookie(adminCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());
    }

    // ----- 삭제 -----

    @Test
    @DisplayName("사용자 삭제 → 204, 세션·기록까지 연쇄 삭제")
    void deleteUser_cascades() throws Exception {
        Cafe cafe = cafeRepository.save(new Cafe("삭제카페", "DEL-QR"));
        sessionRepository.saveAndFlush(new CheckInSession(normal, cafe, Instant.now().minusSeconds(3600)));

        mockMvc.perform(delete("/api/admin/users/" + normal.getId()).cookie(adminCookie))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertTrue(userRepository.findById(normal.getId()).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(
                sessionRepository.findActiveByUserId(normal.getId()).isEmpty());
    }

    @Test
    @DisplayName("본인 삭제 금지 → 400")
    void cannotDeleteSelf() throws Exception {
        mockMvc.perform(delete("/api/admin/users/" + admin.getId()).cookie(adminCookie))
                .andExpect(status().isBadRequest());
    }

    // ----- 경고 리셋 / 강제 체크아웃 -----

    @Test
    @DisplayName("경고 리셋 → 204, 경고 0")
    void resetWarnings() throws Exception {
        normal.addWarning(202607);
        userRepository.saveAndFlush(normal);
        mockMvc.perform(post("/api/admin/users/" + normal.getId() + "/warnings/reset").cookie(adminCookie))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(
                0, userRepository.findById(normal.getId()).orElseThrow().effectiveWarnings(202607));
    }

    @Test
    @DisplayName("강제 체크아웃 → 204, 열린 세션이 FORCE_CLOSED")
    void forceCheckout() throws Exception {
        Cafe cafe = cafeRepository.save(new Cafe("강제카페", "FORCE-QR"));
        CheckInSession s = sessionRepository.saveAndFlush(
                new CheckInSession(normal, cafe, Instant.now().minusSeconds(3600)));
        mockMvc.perform(post("/api/admin/users/" + normal.getId() + "/force-checkout").cookie(adminCookie))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(
                SessionStatus.FORCE_CLOSED, sessionRepository.findById(s.getId()).orElseThrow().getStatus());
    }

    // ----- 학교 -----

    @Test
    @DisplayName("학교 생성 → 201, 중복 이름 → 409")
    void createSchool_andDuplicate() throws Exception {
        mockMvc.perform(post("/api/admin/schools").cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"신규대\",\"shortName\":\"신규\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("신규대"));
        mockMvc.perform(post("/api/admin/schools").cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"신규대\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("학교 삭제 → 소속 학생은 무소속으로")
    void deleteSchool_movesMembersToNull() throws Exception {
        School school = schoolRepository.save(new School("폐교대", "폐교"));
        normal.setSchool(school);
        userRepository.saveAndFlush(normal);

        mockMvc.perform(delete("/api/admin/schools/" + school.getId()).cookie(adminCookie))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertTrue(schoolRepository.findById(school.getId()).isEmpty());
        org.junit.jupiter.api.Assertions.assertNull(
                userRepository.findById(normal.getId()).orElseThrow().getSchool());
    }

    // ----- QR 재발급 / 배치 -----

    @Test
    @DisplayName("QR 재발급 → 새 토큰")
    void rotateQr() throws Exception {
        Cafe cafe = cafeRepository.save(new Cafe("QR카페", "OLD-QR-TOKEN"));
        mockMvc.perform(post("/api/admin/cafes/" + cafe.getId() + "/rotate-qr").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrToken").value(org.hamcrest.Matchers.not("OLD-QR-TOKEN")));
    }

    @Test
    @DisplayName("배치 수동 실행 → 200, closed 수 반환")
    void runBatch() throws Exception {
        mockMvc.perform(post("/api/admin/batch/daily-close").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").exists());
    }
}
