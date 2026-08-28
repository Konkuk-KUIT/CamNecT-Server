package CamNecT.server.domain.chat.service;

import CamNecT.server.domain.chat.dto.message.ChatMessageResponseDto;
import CamNecT.server.domain.chat.dto.message.ChatReadEvent;
import CamNecT.server.domain.chat.dto.room.ChatRoomListUpdateDto;
import CamNecT.server.domain.chat.event.ChatMessageCommittedEvent;
import CamNecT.server.domain.chat.event.ChatReadCommittedEvent;
import CamNecT.server.domain.chat.event.ChatRoomClosedCommittedEvent;
import CamNecT.server.domain.chat.repository.ChatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRealtimeDeliveryServiceTest {

    @Mock ChatRepository chatRepository;
    @Mock SimpMessagingTemplate messagingTemplate;

    @InjectMocks ChatRealtimeDeliveryService deliveryService;

    @Test
    void messageDeliveryUpdatesRoomAndBothRoomLists() {
        ChatMessageCommittedEvent event = messageEvent();
        when(chatRepository.countByRoom_IdAndReceiver_UserIdAndIsReadFalse(99L, 2L)).thenReturn(4L);
        when(chatRepository.countByRoom_IdAndReceiver_UserIdAndIsReadFalse(99L, 1L)).thenReturn(3L);
        when(chatRepository.countVisibleUnreadByUserId(2L)).thenReturn(7L);
        when(chatRepository.countVisibleUnreadByUserId(1L)).thenReturn(2L);

        deliveryService.deliverMessage(event);

        verify(messagingTemplate).convertAndSend("/sub/chat/room/99", event.message());
        verify(messagingTemplate).convertAndSend(eq("/sub/user/2/rooms"), any(Object.class));
        ArgumentCaptor<ChatRoomListUpdateDto> senderUpdate =
                ArgumentCaptor.forClass(ChatRoomListUpdateDto.class);
        verify(messagingTemplate).convertAndSend(eq("/sub/user/1/rooms"), senderUpdate.capture());
        assertThat(senderUpdate.getValue().getUnreadCount()).isEqualTo(3L);
    }

    @Test
    void oneSocketFailureDoesNotStopOtherDestinations() {
        ChatMessageCommittedEvent event = messageEvent();
        doThrow(new IllegalStateException("room socket down"))
                .when(messagingTemplate).convertAndSend("/sub/chat/room/99", event.message());

        deliveryService.deliverMessage(event);

        verify(messagingTemplate).convertAndSend(eq("/sub/user/2/rooms"), any(Object.class));
        verify(messagingTemplate).convertAndSend(eq("/sub/user/1/rooms"), any(Object.class));
    }

    @Test
    void readDeliveryUsesVisibleUnreadCountAndUpdatesRoomAndReaderList() {
        ChatReadEvent readEvent = ChatReadEvent.of(99L, 20L, "2026-01-01T10:01:00");
        ChatReadCommittedEvent event = new ChatReadCommittedEvent(
                readEvent, 2L, "last", "2026-01-01T10:00:00");
        when(chatRepository.countVisibleUnreadByUserId(2L)).thenReturn(3L);

        deliveryService.deliverRead(event);

        verify(messagingTemplate).convertAndSend("/sub/chat/room/99", readEvent);
        verify(messagingTemplate).convertAndSend(eq("/sub/user/2/rooms"), any(Object.class));
        verify(chatRepository).countVisibleUnreadByUserId(2L);
    }

    @Test
    void roomClosedDeliveryUsesRoomSubscriptionAndExactPayload() {
        ChatRoomClosedCommittedEvent event = new ChatRoomClosedCommittedEvent(99L);

        deliveryService.deliverRoomClosed(event);

        verify(messagingTemplate).convertAndSend(
                "/sub/chat/room/99", event.closedEvent());
        verifyNoInteractions(chatRepository);
    }

    private ChatMessageCommittedEvent messageEvent() {
        ChatMessageResponseDto response = ChatMessageResponseDto.builder()
                .messageId(10L)
                .roomId(99L)
                .senderId(1L)
                .receiverId(2L)
                .message("hello")
                .sendDate("2026-01-01T10:00:00")
                .build();
        return new ChatMessageCommittedEvent(
                response, 1L, 2L, "hello", "2026-01-01T10:00:00");
    }
}
