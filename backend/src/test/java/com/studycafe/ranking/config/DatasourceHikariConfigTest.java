package com.studycafe.ranking.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * 운영 {@code application.yml} 의 Hikari 풀 설정이 Neon(서버리스 Postgres) scale-to-zero 를 위한 값으로
 * 바인딩되는지 검증한다.
 *
 * <p>테스트 classpath 의 {@code application.yml} 은 datasource 를 H2 로 덮으므로(그래서 @SpringBootTest 는
 * 운영 Hikari 설정을 로드하지 않는다), 운영 파일을 직접 읽어 <b>Spring 바인딩만</b> 확인한다 — DB 연결 없이
 * 순수 바인딩. kebab-case 키(minimum-idle 등)가 HikariCP 프로퍼티로 제대로 매핑되는지, 버전 차이로 값이
 * 달라지지 않는지를 잡는다. 값이 바뀌면 무료 컴퓨트 절약(§Neon scale-to-zero)이 조용히 깨지므로 회귀 방지용.
 */
class DatasourceHikariConfigTest {

    @Test
    void productionHikariPoolBindsForNeonScaleToZero() throws IOException {
        // 실행 위치가 backend/ 든 레포 루트든 운영 파일을 찾도록 두 경로를 시도한다.
        FileSystemResource resource = new FileSystemResource("src/main/resources/application.yml");
        if (!resource.exists()) {
            resource = new FileSystemResource("backend/src/main/resources/application.yml");
        }
        assertThat(resource.exists())
                .as("운영 application.yml 을 찾지 못했습니다: " + resource.getPath())
                .isTrue();

        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("main-application", resource);
        Binder binder = new Binder(ConfigurationPropertySources.from(sources));

        HikariConfig hikari = binder
                .bind("spring.datasource.hikari", Bindable.of(HikariConfig.class))
                .orElseThrow(() -> new AssertionError("spring.datasource.hikari 설정을 찾지 못했습니다"));

        // 유휴 시 커넥션 0개까지 축소 → 열린 커넥션이 없어야 Neon autosuspend(5분)가 동작한다.
        assertThat(hikari.getMinimumIdle()).isZero();
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(5);
        // autosuspend(5분)보다 먼저 유휴 커넥션을 닫도록 60초.
        assertThat(hikari.getIdleTimeout()).isEqualTo(60_000L);
        // keepalive-time 은 명시하지 않아 기본 0(비활성) — 주기 핑으로 DB 를 깨우지 않는다.
        assertThat(hikari.getKeepaliveTime()).isZero();
    }
}
