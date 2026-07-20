package com.studycafe.ranking.system;

import com.studycafe.ranking.batch.CatchUpStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /healthz 의 실제 웹 계약을 검증한다: 공개(무인증) 접근, HTTP 200(라이브니스/keep-alive 유지),
 * JSON 직렬화, healthy/degraded 본문 형태. degraded 여도 상태코드는 200 이어야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatchUpStatus catchUpStatus;

    @Test
    void healthzIsPublicAndReportsOkWhenHealthy() throws Exception {
        when(catchUpStatus.snapshot()).thenReturn(new CatchUpStatus.Snapshot(false, null));

        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.batchCatchUpFailingSince").doesNotExist());
    }

    @Test
    void healthzReports200WithDegradedAndTimestampWhenBatchFailing() throws Exception {
        Instant since = Instant.parse("2026-07-20T19:00:00Z");
        when(catchUpStatus.snapshot()).thenReturn(new CatchUpStatus.Snapshot(true, since));

        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk()) // degraded 여도 200 — Render 라이브니스/keep-alive 불변
                .andExpect(jsonPath("$.status").value("degraded"))
                .andExpect(jsonPath("$.batchCatchUpFailingSince").value(since.toString()));
    }
}
