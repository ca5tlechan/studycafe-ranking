package com.studycafe.ranking.push;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

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

    private PushSubscription sub(String endpoint) {
        return new PushSubscription(new User("u", "h", "n", 1, null), endpoint, "p256dh", "auth");
    }

    @Test
    void sendToUsers_sendsToEverySubscription_andPrunesOnlyGoneOnes() {
        PushSubscription ok = sub("https://push/ok");
        PushSubscription gone = sub("https://push/gone");
        PushSubscription failed = sub("https://push/failed");
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

    @Test
    void subscribe_newEndpoint_saves() {
        when(repository.findByEndpoint("e")).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(new User("u", "h", "n", 1, null));

        service.subscribe(1L, new SubscribeRequest("e", new SubscribeRequest.Keys("p", "a")));

        verify(repository).save(any(PushSubscription.class));
    }

    @Test
    void subscribe_existingEndpoint_refreshesInsteadOfInserting() {
        PushSubscription existing = sub("e");
        when(repository.findByEndpoint("e")).thenReturn(Optional.of(existing));
        User owner = new User("u2", "h", "n", 1, null);
        when(userRepository.getReferenceById(2L)).thenReturn(owner);

        service.subscribe(2L, new SubscribeRequest("e", new SubscribeRequest.Keys("newP", "newA")));

        verify(repository, never()).save(any());
        assertThat(existing.getP256dh()).isEqualTo("newP");
        assertThat(existing.getAuth()).isEqualTo("newA");
        assertThat(existing.getUser()).isSameAs(owner);
    }
}
