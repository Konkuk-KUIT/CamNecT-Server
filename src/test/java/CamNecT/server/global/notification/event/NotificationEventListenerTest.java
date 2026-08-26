package CamNecT.server.global.notification.event;

import CamNecT.server.global.notification.model.NotificationType;
import CamNecT.server.global.notification.service.NotificationService;
import CamNecT.server.global.notification.service.NotificationWsPublisher;
import CamNecT.server.global.notification.service.PushDeviceService;
import CamNecT.server.global.notification.util.FCMSender;
import CamNecT.server.global.notification.util.NotificationLinkResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    NotificationService notificationService;

    @Mock
    FCMSender fcmSender;

    @Mock
    PushDeviceService pushDeviceService;

    @Mock
    NotificationWsPublisher notificationWsPublisher;

    @Mock
    NotificationLinkResolver notificationLinkResolver;

    @InjectMocks
    NotificationEventListener listener;

    @Test
    void persistSkipsSelfNotification() {
        NotifiableEvent event = event(1L, 1L);

        listener.persist(event);

        verifyNoInteractions(
                notificationService,
                notificationLinkResolver
        );
    }

    @Test
    void persistStoresResolvedLink() {
        NotifiableEvent event = event(2L, 1L);

        when(notificationLinkResolver.resolveOrFallback(event))
                .thenReturn("/community/posts/10");

        listener.persist(event);

        verify(notificationService).create(
                2L,
                1L,
                NotificationType.POST_COMMENTED,
                "새 댓글",
                10L,
                20L,
                null,
                "/community/posts/10"
        );
    }

    @Test
    void pushContinuesToFcmWhenWebSocketFails() throws Exception {
        NotifiableEvent event = event(2L, 1L);

        when(notificationLinkResolver.resolveOrFallback(event))
                .thenReturn("/community/posts/10");

        doThrow(new IllegalStateException("ws down"))
                .when(notificationWsPublisher)
                .sendToUser(eq(2L), any());

        when(pushDeviceService.findEnabledTokens(2L))
                .thenReturn(List.of("token"));

        when(fcmSender.sendToTokens(
                eq(List.of("token")),
                anyMap()
        )).thenReturn(
                new FCMSender.SendResult(
                        1,
                        1,
                        0,
                        List.of()
                )
        );

        assertDoesNotThrow(() -> listener.push(event));

        verify(fcmSender).sendToTokens(
                eq(List.of("token")),
                anyMap()
        );

        verify(pushDeviceService, never())
                .disableTokens(anyList());
    }

    @Test
    void pushDisablesOnlyInvalidTokensReturnedByFcm() throws Exception {
        NotifiableEvent event = event(2L, 1L);
        when(notificationLinkResolver.resolveOrFallback(event)).thenReturn("/community/posts/10");
        when(pushDeviceService.findEnabledTokens(2L)).thenReturn(List.of("valid", "invalid"));
        when(fcmSender.sendToTokens(eq(List.of("valid", "invalid")), anyMap()))
                .thenReturn(new FCMSender.SendResult(2, 1, 1, List.of("invalid")));

        listener.push(event);

        verify(pushDeviceService).disableTokens(List.of("invalid"));
    }

    @Test
    void pushDoesNotPropagateTokenLookupFailure() {
        NotifiableEvent event = event(2L, 1L);

        when(notificationLinkResolver.resolveOrFallback(event))
                .thenReturn("/community/posts/10");

        when(pushDeviceService.findEnabledTokens(2L))
                .thenThrow(
                        new IllegalStateException("database down")
                );

        assertDoesNotThrow(() -> listener.push(event));

        verifyNoInteractions(fcmSender);
    }

    @Test
    void invalidEventIsSkippedBeforePersistenceAndDelivery() {
        NotifiableEvent invalid = SimpleNotifiableEvent.of(
                null, 1L, NotificationType.POST_COMMENTED, "message", 10L, null
        );

        listener.persist(invalid);
        listener.push(invalid);

        verifyNoInteractions(notificationService, notificationLinkResolver,
                notificationWsPublisher, pushDeviceService, fcmSender);
    }

    @Test
    void chatPushContainsExplicitRoomId() throws Exception {
        NotifiableEvent event = new NewChatMessageEvent(2L, 1L, 99L, "message");
        when(notificationLinkResolver.resolveOrFallback(event)).thenReturn("/chat/99");
        when(pushDeviceService.findEnabledTokens(2L)).thenReturn(List.of("token"));
        when(fcmSender.sendToTokens(eq(List.of("token")), anyMap()))
                .thenReturn(new FCMSender.SendResult(1, 1, 0, List.of()));

        listener.push(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fcmSender).sendToTokens(eq(List.of("token")), dataCaptor.capture());
        assertEquals("99", dataCaptor.getValue().get("roomId"));
        assertEquals("/chat/99", dataCaptor.getValue().get("link"));
    }

    @Test
    void chatMessageIsPushOnlyAndIsNotPersistedInHiddenNotificationList() {
        NotifiableEvent event = new NewChatMessageEvent(2L, 1L, 99L, "message");

        listener.persist(event);

        verifyNoInteractions(notificationService, notificationLinkResolver);
    }

    private static NotifiableEvent event(
            Long receiverId,
            Long actorId
    ) {
        return SimpleNotifiableEvent.of(
                receiverId,
                actorId,
                NotificationType.POST_COMMENTED,
                "새 댓글",
                10L,
                20L
        );
    }
}
