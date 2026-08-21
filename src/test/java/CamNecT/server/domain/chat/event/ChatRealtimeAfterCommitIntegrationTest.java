package CamNecT.server.domain.chat.event;

import CamNecT.server.domain.chat.dto.message.ChatMessageResponseDto;
import CamNecT.server.domain.chat.service.ChatRealtimeDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("test")
class ChatRealtimeAfterCommitIntegrationTest {

    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean ChatRealtimeDeliveryService deliveryService;

    @Test
    void realtimeDeliveryStartsOnlyAfterTransactionCommit() {
        ChatMessageCommittedEvent event = messageEvent();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            verifyNoInteractions(deliveryService);
        });

        verify(deliveryService).deliverMessage(event);
    }

    @Test
    void rollbackSuppressesRealtimeDelivery() {
        ChatMessageCommittedEvent event = messageEvent();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        verifyNoInteractions(deliveryService);
    }

    @Test
    void roomClosedDeliveryStartsOnlyAfterTransactionCommit() {
        ChatRoomClosedCommittedEvent event = new ChatRoomClosedCommittedEvent(99L);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            verifyNoInteractions(deliveryService);
        });

        verify(deliveryService).deliverRoomClosed(event);
    }

    @Test
    void rollbackSuppressesRoomClosedDelivery() {
        ChatRoomClosedCommittedEvent event = new ChatRoomClosedCommittedEvent(99L);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        verifyNoInteractions(deliveryService);
    }

    private ChatMessageCommittedEvent messageEvent() {
        ChatMessageResponseDto message = ChatMessageResponseDto.builder()
                .messageId(10L)
                .roomId(99L)
                .clientMessageId("0e9e31aa-99e7-4c58-90d8-f939b56fd234")
                .senderId(1L)
                .receiverId(2L)
                .message("hello")
                .sendDate("2026-01-01T10:00:00")
                .build();
        return new ChatMessageCommittedEvent(
                message, 1L, 2L, "hello", "2026-01-01T10:00:00");
    }
}
