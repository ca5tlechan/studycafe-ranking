package com.studycafe.ranking.batch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@code @Async} readiness 계약을 <b>Spring 프록시 경유</b>로 고정한다. 단위 테스트(StartupCatchUpTest)는
 * 객체를 직접 호출해 프록시를 안 거치므로, {@code @Async}/{@code @EnableAsync} 가 제거돼도 통과한다 —
 * 여기선 컨텍스트가 주입한 프록시 빈을 호출해 closeOverdue 가 <b>호출부가 아닌 다른 스레드</b>에서 도는지
 * (= readiness 를 막지 않는지) 검증한다.
 */
@SpringBootTest
class StartupCatchUpAsyncTest {

    @MockitoBean
    private DailyCloseService dailyCloseService;

    @Autowired
    private StartupCatchUp startupCatchUp; // @Async 프록시로 주입됨

    @Test
    void catchUpRunsOnBackgroundThreadNotTheCaller() throws InterruptedException {
        CountDownLatch called = new CountDownLatch(1);
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        when(dailyCloseService.closeOverdue(any())).thenAnswer(invocation -> {
            workerThread.set(Thread.currentThread());
            called.countDown();
            return 0;
        });
        Thread caller = Thread.currentThread();

        startupCatchUp.catchUpOnStartup(); // 프록시 → 즉시 반환, 실제 작업은 백그라운드

        assertThat(called.await(5, TimeUnit.SECONDS)).isTrue();
        // 호출 스레드가 아닌 다른 스레드에서 실행됨 = @Async 로 기동/readiness 를 막지 않음.
        // (@Async 가 빠지면 호출부 스레드에서 동기 실행되어 이 단언이 깨진다.)
        assertThat(workerThread.get()).isNotSameAs(caller);
    }
}
