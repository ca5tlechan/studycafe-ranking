package com.studycafe.ranking.batch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 기동 catch-up 의 이벤트+비동기 계약을 <b>Spring 프록시/이벤트 경유</b>로 고정한다.
 * <ul>
 *   <li>{@code @EventListener(ApplicationReadyEvent.class)}: 준비 이벤트를 발행하면 closeOverdue 가 불린다.
 *   <li>{@code @Async}: closeOverdue 가 <b>발행 스레드가 아닌 다른 스레드</b>에서 돈다(=readiness 미차단).
 * </ul>
 * 둘 중 하나라도 제거되면 이 테스트가 깨진다(이벤트 미발화 → 타임아웃 / 동기 실행 → 같은 스레드).
 */
@SpringBootTest
class StartupCatchUpAsyncTest {

    @MockitoBean
    private DailyCloseService dailyCloseService;

    @Autowired
    private ConfigurableApplicationContext context;

    @Test
    void applicationReadyEventTriggersCatchUpOnBackgroundThread() throws InterruptedException {
        CountDownLatch called = new CountDownLatch(1);
        AtomicReference<Thread> workerThread = new AtomicReference<>();
        when(dailyCloseService.closeOverdue(any())).thenAnswer(invocation -> {
            workerThread.set(Thread.currentThread());
            called.countDown();
            return 0;
        });
        Thread publisher = Thread.currentThread();

        context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[] {}, context, Duration.ZERO));

        // 이벤트가 리스너를 트리거해 closeOverdue 가 불린다(@EventListener 제거 시 여기서 타임아웃).
        assertThat(called.await(5, TimeUnit.SECONDS)).isTrue();
        // 발행 스레드가 아닌 다른 스레드에서 실행됨(@Async 제거 시 동기 실행되어 같은 스레드가 되어 깨진다).
        assertThat(workerThread.get()).isNotSameAs(publisher);
    }
}
