package com.studycafe.ranking.auth;

import com.studycafe.ranking.auth.dto.LoginRequest;
import com.studycafe.ranking.auth.dto.LoginResponse;
import com.studycafe.ranking.auth.dto.SignupRequest;
import com.studycafe.ranking.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieFactory authCookieFactory;

    public AuthController(AuthService authService, AuthCookieFactory authCookieFactory) {
        this.authService = authService;
        this.authCookieFactory = authCookieFactory;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    /**
     * 로그인 성공 시 JWT 를 HttpOnly 쿠키로 내려준다(이슈 #7). 토큰은 응답 본문에 싣지 않는다.
     * 본문은 화면 표시에 필요한 사용자 정보만.
     */
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse res = authService.login(request);
        ResponseCookie cookie = authCookieFactory.create(res.token());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(res.user());
    }

    /**
     * 인증 쿠키를 제거한다. 인증 필요(SecurityConfig) — 공개면 외부 form POST 로 강제 로그아웃 CSRF 가
     * 가능하다. SameSite=Strict 라 크로스사이트 요청엔 쿠키가 실리지 않아, 공격자의 로그아웃 요청은
     * 인증 없이 도달해 401 이 되고 쿠키가 지워지지 않는다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = authCookieFactory.clear();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}
