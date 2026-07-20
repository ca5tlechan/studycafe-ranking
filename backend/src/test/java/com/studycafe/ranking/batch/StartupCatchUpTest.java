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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StartupCatchUp 의 계약: 기동 시 현재 시각으로 closeOverdue 를 위임하고, 실패해도 기동을 막지 않는다.
 * (04:00 경계 분할·다중 세션 합산·멱등·10분 필터의 실제 검증은 {@link DailyCloseServiceTest} 담당 —
 *  여기서 중복하지 않는다.)
 */
@ExtendWith(MockitoExtension.class)
class StartupCatchUpTest {

    @Mock
    private DailyCloseService dailyCloseService;

    @Test
    void delegatesToCloseOverdueWithCurrentInstant() {
        when(dailyCloseService.closeOverdue(any())).thenReturn(3);
        StartupCatchUp startupCatchUp = new StartupCatchUp(dailyCloseService, 3, 0);
        Instant before = Instant.now();

        startupCatchUp.catchUpOnStartup();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(dailyCloseService).closeOverdue(captor.capture());
        // 기동 시각(대략 now)으로 호출한다 — 04:00 경계 계산의 기준.
        assertThat(captor.getValue())
                .isBetween(before.minus(1, ChronoUnit.MINUTES), Instant.now().plus(1, ChronoUnit.MINUTES));
    }

    @Test
    void retriesOnTransientFailureThenGivesUpWithoutThrowing() {
        when(dailyCloseService.closeOverdue(any())).thenThrow(new RuntimeException("DB down at boot"));
        StartupCatchUp startupCatchUp = new StartupCatchUp(dailyCloseService, 3, 0); // backoff 0 → 즉시

        // 재시도를 소진해도 기동을 막지 않는다(예외 전파 X).
        assertThatCode(startupCatchUp::catchUpOnStartup).doesNotThrowAnyException();
        verify(dailyCloseService, times(3)).closeOverdue(any());
    }
}
