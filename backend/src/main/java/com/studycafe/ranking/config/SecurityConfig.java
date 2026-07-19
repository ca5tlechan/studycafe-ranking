package com.studycafe.ranking.config;

import com.studycafe.ranking.auth.JwtAuthenticationFilter;
import com.studycafe.ranking.auth.JwtTokenProvider;
import com.studycafe.ranking.auth.RestAccessDeniedHandler;
import com.studycafe.ranking.auth.RestAuthenticationEntryPoint;
import com.studycafe.ranking.repository.UserRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenProvider tokenProvider,
                                                   UserRepository userRepository,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // CSRF 토큰 미사용: 인증 쿠키가 SameSite=Strict 라 크로스사이트 요청엔 쿠키가 실리지
                // 않아, 인증을 요구하는 상태 변경 요청은 쿠키 없이 도달해 401 이 된다(AuthCookieFactory 참고).
                // 단, SameSite 는 응답의 Set-Cookie(쿠키 삭제 포함) 적용까지 막지는 못하므로 공개
                // 상태변경 엔드포인트를 두면 안 된다 — 로그아웃도 인증을 요구해 강제 로그아웃 CSRF 를 막는다.
                // 오리진 분리 배포로 가면 SameSite=None + CSRF 토큰이 필요하다(이슈 #7).
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 로그인/가입만 공개. 로그아웃은 인증 필요(공개 시 외부 form POST 로 강제 로그아웃 가능).
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/schools").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN") // 관리자 전용
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint) // 미인증 → 401
                        .accessDeniedHandler(accessDeniedHandler))          // 인증됐으나 권한 없음 → 403
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, userRepository),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
