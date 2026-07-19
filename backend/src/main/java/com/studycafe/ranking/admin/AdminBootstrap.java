package com.studycafe.ranking.admin;

import com.studycafe.ranking.domain.Role;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초기 관리자 부트스트랩: 환경변수 {@code ADMIN_LOGIN_ID} 로 지정한 계정을 기동 시 ADMIN 으로 승격한다.
 * 그 뒤로는 관리자가 관리자 화면에서 다른 계정에 role 을 부여/회수한다.
 *
 * <p>계정이 아직 없으면(가입 전) 아무 것도 하지 않는다 — 해당 사용자가 앱에서 가입한 뒤 다음 기동에
 * 승격된다. 이미 ADMIN 이면 그대로 둔다(멱등).
 */
@Component
@Order(20) // DataInitializer(시드) 이후
class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final String adminLoginId;

    AdminBootstrap(UserRepository userRepository,
                   @Value("${app.admin.login-id:}") String adminLoginId) {
        this.userRepository = userRepository;
        this.adminLoginId = adminLoginId;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (adminLoginId == null || adminLoginId.isBlank()) {
            return; // 미설정 — 부트스트랩 관리자 없음
        }
        userRepository.findByLoginId(adminLoginId.trim()).ifPresentOrElse(user -> {
            if (!user.isAdmin()) {
                user.changeRole(Role.ADMIN);
                log.info("초기 관리자 승격: loginId={}", user.getLoginId());
            }
        }, () -> log.warn("app.admin.login-id={} 계정이 아직 없어 관리자 승격을 건너뜀(가입 후 재기동 시 승격)", adminLoginId));
    }
}
