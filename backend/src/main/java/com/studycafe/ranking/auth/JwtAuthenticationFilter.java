package com.studycafe.ranking.auth;

import com.studycafe.ranking.domain.Role;
import com.studycafe.ranking.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * HttpOnly 인증 쿠키(scr_token)의 JWT 를 검증해 SecurityContext 에 userId(principal)와 권한을 세팅한다(이슈 #7).
 * 토큰이 없거나 유효하지 않으면 인증 없이 통과시키고, 보호된 엔드포인트에서 401 처리된다.
 *
 * <p>권한(role)은 JWT 클레임이 아니라 매 요청 DB 에서 읽는다: grant/revoke 와 사용자 삭제가 다음 요청부터
 * 즉시 반영되게 하기 위함이다(토큰에 담으면 만료까지 최대 7일간 스테일). 파일럿 규모(수십 명)라 PK 조회
 * 한 번의 부담은 무시할 수 있다. 사용자가 삭제됐으면 role 이 없어 인증이 무효화된다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            try {
                Long userId = tokenProvider.parseUserId(token);
                Optional<Role> role = userRepository.findRoleById(userId);
                if (role.isEmpty()) {
                    // 토큰은 유효하나 사용자가 삭제됨 → 인증 없이 통과(401)
                    SecurityContextHolder.clearContext();
                } else {
                    List<SimpleGrantedAuthority> authorities = role.get() == Role.ADMIN
                            ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                            : List.of();
                    var authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // 유효하지 않은/파싱 불가 토큰 → 인증 없이 통과(보호된 엔드포인트에서 401)
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookieFactory.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
