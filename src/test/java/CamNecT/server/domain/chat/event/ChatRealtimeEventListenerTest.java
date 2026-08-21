package CamNecT.server.domain.chat.event;

import CamNecT.server.domain.chat.dto.message.ChatMessageResponseDto;
import CamNecT.server.domain.chat.service.ChatRealtimeDeliveryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class ChatRealtimeEventListenerTest {

    @Test
    void deliveryInfrastructureFailureDoesNotEscapeAfterCommitListener() {
        ChatRealtimeDeliveryService deliveryService = mock(ChatRealtimeDeliveryService.class);
        ChatRealtimeEventListener listener = new ChatRealtimeEventListener(deliveryService);
        ChatMessageResponseDto message = ChatMessageResponseDto.builder().roomId(99L).build();
        ChatMessageCommittedEvent event = new ChatMessageCommittedEvent(
                message, 1L, 2L, "hello", "2026-01-01T10:00:00");
        doThrow(new IllegalStateException("delivery transaction unavailable"))
                .when(deliveryService).deliverMessage(event);

        assertDoesNotThrow(() -> listener.handleMessageCommitted(event));
    }

    @Test
    void roomClosedDeliveryFailureDoesNotEscapeAfterCommitListener() {
        ChatRealtimeDeliveryService deliveryService = mock(ChatRealtimeDeliveryService.class);
        ChatRealtimeEventListener listener = new ChatRealtimeEventListener(deliveryService);
        ChatRoomClosedCommittedEvent event = new ChatRoomClosedCommittedEvent(99L);
        doThrow(new IllegalStateException("broker unavailable"))
                .when(deliveryService).deliverRoomClosed(event);

        assertDoesNotThrow(() -> listener.handleRoomClosedCommitted(event));
    }
}
