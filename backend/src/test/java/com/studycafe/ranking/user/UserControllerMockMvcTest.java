package com.studycafe.ranking.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studycafe.ranking.auth.AuthCookieFactory;
import com.studycafe.ranking.auth.JwtTokenProvider;
import com.studycafe.ranking.domain.School;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.SchoolRepository;
import com.studycafe.ranking.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 유저 본인 소속(학교) 변경 API — 인증 필요 + 학교 지정/무소속 반영. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerMockMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private User user;
    private Cookie cookie;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("me_school_mvc", "{noop}pw", "김소속", 1, null));
        cookie = new Cookie(AuthCookieFactory.COOKIE_NAME, jwtTokenProvider.createToken(user.getId()));
    }

    @Test
    @DisplayName("본인 소속 변경 — 무인증 401, 인증 시 학교 지정/무소속 204 반영")
    void changeMySchool() throws Exception {
        School school = schoolRepository.save(new School("본인변경테스트고", "본인고"));
        String url = "/api/users/me/school";

        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"schoolId\":null}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put(url).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolId\":" + school.getId() + "}"))
                .andExpect(status().isNoContent());
        assertEquals(school.getId(),
                userRepository.findById(user.getId()).orElseThrow().getSchool().getId());

        mockMvc.perform(put(url).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolId\":null}"))
                .andExpect(status().isNoContent());
        assertNull(userRepository.findById(user.getId()).orElseThrow().getSchool());
    }
}
