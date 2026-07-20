package com.studycafe.ranking.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StartupCatchUp 의 계약: 기동 시 현재 시각으로 closeOverdue 를 위임하고, 성공 시 healthy·소진 시 degraded 로
 * 상태를 남기며, 실패해도 기동을 막지 않는다. (04:00 경계 분할·멱등·10분 필터의 실제 검증은
 * {@link DailyCloseServiceTest} 담당 — 여기서 중복하지 않는다.)
 */
@ExtendWith(MockitoExtension.class)
class StartupCatchUpTest {

    @Mock
    private DailyCloseService dailyCloseService;

    private final CatchUpStatus catchUpStatus = new CatchUpStatus();

    @Test
    void delegatesToCloseOverdueWithCurrentInstantAndStaysHealthy() {
        when(dailyCloseService.closeOverdue(any())).thenReturn(3);
        StartupCatchUp startupCatchUp = new StartupCatchUp(dailyCloseService, catchUpStatus, 3, 0);
        Instant before = Instant.now();

        startupCatchUp.catchUpOnStartup();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(dailyCloseService).closeOverdue(captor.capture());
        assertThat(captor.getValue())
                .isBetween(before.minus(1, ChronoUnit.MINUTES), Instant.now().plus(1, ChronoUnit.MINUTES));
        assertThat(catchUpStatus.isDegraded()).isFalse();
    }

    @Test
    void retriesOnTransientFailureThenGivesUpWithoutThrowingAndMarksDegraded() {
        when(dailyCloseService.closeOverdue(any())).thenThrow(new RuntimeException("DB down at boot"));
        StartupCatchUp startupCatchUp = new StartupCatchUp(dailyCloseService, catchUpStatus, 3, 0); // backoff 0

        // 재시도를 소진해도 기동을 막지 않되(예외 전파 X), 실패 상태를 노출한다.
        assertThatCode(startupCatchUp::catchUpOnStartup).doesNotThrowAnyException();
        verify(dailyCloseService, times(3)).closeOverdue(any());
        assertThat(catchUpStatus.isDegraded()).isTrue();
    }

    @Test
    void clearsDegradedOnceCatchUpSucceeds() {
        catchUpStatus.markFailed(); // 앞선 실패로 degraded 라고 가정
        when(dailyCloseService.closeOverdue(any())).thenReturn(0);

        new StartupCatchUp(dailyCloseService, catchUpStatus, 3, 0).catchUpOnStartup();

        assertThat(catchUpStatus.isDegraded()).isFalse();
    }

    @Test
    void marksDegradedWhenInterruptedDuringBackoff() {
        when(dailyCloseService.closeOverdue(any())).thenThrow(new RuntimeException("fail"));
        StartupCatchUp startupCatchUp = new StartupCatchUp(dailyCloseService, catchUpStatus, 3, 1000); // backoff>0 → sleep
        Thread.currentThread().interrupt(); // 다음 sleep 에서 즉시 InterruptedException
        try {
            startupCatchUp.catchUpOnStartup();

            // 첫 시도 실패 → backoff sleep 인터럽트 → 미완료 상태를 degraded 로 남기고 중단.
            verify(dailyCloseService, times(1)).closeOverdue(any());
            assertThat(catchUpStatus.isDegraded()).isTrue();
        } finally {
            Thread.interrupted(); // 인터럽트 플래그 정리(다른 테스트 오염 방지)
        }
    }

    @Test
    void rejectsInvalidRetryConfiguration() {
        assertThatThrownBy(() -> new StartupCatchUp(dailyCloseService, catchUpStatus, 0, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StartupCatchUp(dailyCloseService, catchUpStatus, 3, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
