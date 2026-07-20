package com.studycafe.ranking.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartupCatchUpTest {

    @Mock
    private DailyCloseService dailyCloseService;
    @InjectMocks
    private StartupCatchUp startupCatchUp;

    @Test
    void runsCloseOverdueOnStartup() {
        when(dailyCloseService.closeOverdue(any())).thenReturn(3);

        startupCatchUp.catchUpOnStartup();

        // 기동 시 밀린 04:00 마감을 보정한다(멱등 catch-up).
        verify(dailyCloseService).closeOverdue(any());
    }

    @Test
    void swallowsExceptionSoStartupNeverFails() {
        when(dailyCloseService.closeOverdue(any())).thenThrow(new RuntimeException("DB down at boot"));

        // 기동 catch-up 실패가 앱 기동을 막으면 안 된다.
        assertThatCode(startupCatchUp::catchUpOnStartup).doesNotThrowAnyException();
    }
}
