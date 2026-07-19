package com.studycafe.ranking.batch;

import com.studycafe.ranking.domain.SessionStatus;
import com.studycafe.ranking.push.PushSubscriptionService;
import com.studycafe.ranking.repository.CheckInSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreCloseNotifyServiceTest {

    @Mock
    private CheckInSessionRepository sessionRepository;
    @Mock
    private PushSubscriptionService pushSubscriptionService;
    @InjectMocks
    private PreCloseNotifyService service;

    @Test
    void notifyActiveUsers_sendsPreCloseMessageToCurrentlyActiveUsers() {
        List<Long> active = List.of(1L, 2L, 3L);
        when(sessionRepository.findUserIdsByStatus(SessionStatus.ACTIVE)).thenReturn(active);
        when(pushSubscriptionService.sendToUsers(active, PreCloseNotifyService.MESSAGE)).thenReturn(5);

        int attempted = service.notifyActiveUsers();

        assertThat(attempted).isEqualTo(5);
        // 대상 = "지금 ACTIVE 인 유저"이고, 보내는 문구는 §3.6b 사전 알림.
        verify(pushSubscriptionService).sendToUsers(active, PreCloseNotifyService.MESSAGE);
    }
}
