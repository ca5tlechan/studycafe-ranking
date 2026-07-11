package com.studycafe.ranking.config;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 유저당 ACTIVE 세션 1개를 DB 레벨에서 보장하는 부분 유니크 인덱스 생성(§4).
 * 부분 인덱스(WHERE)는 Postgres 전용이라 Postgres에서만 실행하고, H2(테스트) 등에선 건너뛴다
 * (그 경우 앱 레벨 토글 검사가 논리적 정합성을 담당).
 * ddl-auto=update 환경의 임시 방편이며, 스키마 안정화 시 Flyway 마이그레이션으로 옮기는 것을 권장.
 */
@Component
class ActiveSessionIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ActiveSessionIndexInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    ActiveSessionIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean postgres = Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) c ->
                c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgresql")));
        if (!postgres) {
            return;
        }
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_active_session_per_user "
                + "ON check_in_sessions (user_id) WHERE status = 'ACTIVE'");
        log.info("Ensured partial unique index ux_active_session_per_user (Postgres)");
    }
}
