package com.studycafe.ranking.system;

import com.studycafe.ranking.batch.CatchUpStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void reportsOkWhenHealthy() {
        Map<String, Object> body = new HealthController(new CatchUpStatus()).healthz();
        assertThat(body).containsEntry("status", "ok");
    }

    @Test
    void reportsDegradedWhenCatchUpFailed() {
        CatchUpStatus status = new CatchUpStatus();
        status.markFailed();

        Map<String, Object> body = new HealthController(status).healthz();

        assertThat(body).containsEntry("status", "degraded").containsKey("batchCatchUpFailingSince");
    }
}
