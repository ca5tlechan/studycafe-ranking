package com.studycafe.ranking.push;

import com.studycafe.ranking.common.exception.InvalidPushEndpointException;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.push.dto.PushDtos.SubscribeRequest;
import com.studycafe.ranking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    private static final String VALID_ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc123";

    @Mock
    private PushSubscriptionRepository repository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WebPushSender sender;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PushSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new PushSubscriptionService(repository, userRepository, sender, objectMapper);
    }

    private PushSubscription subWithOwner(long ownerId, String endpoint) {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);
        return new PushSubscription(owner, endpoint, "p256dh", "auth");
    }

    private SubscribeRequest req(String endpoint) {
        return new SubscribeRequest(endpoint, new SubscribeRequest.Keys("newP", "newA"));
    }

    // ===== 발송 / 정리 =====

    @Test
    void sendToUsers_sendsToEverySubscription_andPrunesOnlyGoneOnes() {
        PushSubscription ok = new PushSubscription(mock(User.class), "https://push/ok", "p", "a");
        PushSubscription gone = new PushSubscription(mock(User.class), "https://push/gone", "p", "a");
        PushSubscription failed = new PushSubscription(mock(User.class), "https://push/failed", "p", "a");
        when(repository.findByUserIdIn(anyCollection())).thenReturn(List.of(ok, gone, failed));
        when(sender.send(eq(ok), anyString())).thenReturn(WebPushSender.Result.OK);
        when(sender.send(eq(gone), anyString())).thenReturn(WebPushSender.Result.GONE);
        when(sender.send(eq(failed), anyString())).thenReturn(WebPushSender.Result.FAILED);

        int attempted = service.sendToUsers(Set.of(1L, 2L), new PushMessage("t", "b", "/checkin"));

        assertThat(attempted).isEqualTo(3);
        // 410/404(GONE)만 정리하고, OK·FAILED 구독은 유지한다.
        verify(repository).deleteByEndpoint("https://push/gone");
        verify(repository, never()).deleteByEndpoint("https://push/ok");
        verify(repository, never()).deleteByEndpoint("https://push/failed");
    }

    @Test
    void sendToUsers_withNoActiveUsers_doesNothing() {
        int attempted = service.sendToUsers(Set.of(), new PushMessage("t", "b", "/"));

        assertThat(attempted).isZero();
        verify(repository, never()).findByUserIdIn(anyCollection());
    }

    // ===== 구독 upsert (소유권) =====

    @Test
    void subscribe_newEndpoint_saves() {
        when(repository.findByEndpoint(VALID_ENDPOINT)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(new User("u", "h", "n", 1, null));

        service.subscribe(1L, req(VALID_ENDPOINT));

        verify(repository).save(any(PushSubscription.class));
    }

    @Test
    void subscribe_existingSameOwner_refreshesInsteadOfInserting() {
        PushSubscription existing = subWithOwner(2L, VALID_ENDPOINT);
        when(repository.findByEndpoint(VALID_ENDPOINT)).thenReturn(Optional.of(existing));
        when(userRepository.getReferenceById(2L)).thenReturn(new User("u2", "h", "n", 1, null));

        service.subscribe(2L, req(VALID_ENDPOINT));

        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
        assertThat(existing.getP256dh()).isEqualTo("newP");
        assertThat(existing.getAuth()).isEqualTo("newA");
    }

    @Test
    void subscribe_existingDifferentOwner_replacesInsteadOfHijacking() {
        PushSubscription foreign = subWithOwner(1L, VALID_ENDPOINT); // 다른 사용자 소유
        when(repository.findByEndpoint(VALID_ENDPOINT)).thenReturn(Optional.of(foreign));
        when(userRepository.getReferenceById(2L)).thenReturn(new User("u2", "h", "n", 1, null));

        service.subscribe(2L, req(VALID_ENDPOINT));

        // 남의 행을 그 자리에서 갱신하지 않고, 지운 뒤 현재 사용자로 새로 만든다.
        verify(repository).delete(foreign);
        verify(repository).save(any(PushSubscription.class));
        assertThat(foreign.getP256dh()).isEqualTo("p256dh"); // 원본 키 그대로(갱신 안 됨)
    }

    @Test
    void subscribe_rejectsNonHttpsOrInternalEndpoint() {
        assertThatThrownBy(() -> service.subscribe(1L, req("http://fcm.googleapis.com/x")))
                .isInstanceOf(InvalidPushEndpointException.class);
        assertThatThrownBy(() -> service.subscribe(1L, req("https://127.0.0.1/x")))
                .isInstanceOf(InvalidPushEndpointException.class);
        // 검증 실패면 저장소를 건드리지 않는다.
        verifyNoInteractions(repository);
    }

    // ===== 해지 (소유자 범위) =====

    @Test
    void unsubscribe_deletesOnlyCallersOwnSubscription() {
        service.unsubscribe(7L, VALID_ENDPOINT);

        verify(repository).deleteByUserIdAndEndpoint(7L, VALID_ENDPOINT);
        verify(repository, never()).deleteByEndpoint(anyString());
    }
}
