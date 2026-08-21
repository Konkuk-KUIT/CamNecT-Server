package CamNecT.server.domain.chat.event;

import CamNecT.server.domain.chat.service.ChatRealtimeDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRealtimeEventListener {

    private final ChatRealtimeDeliveryService deliveryService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessageCommitted(ChatMessageCommittedEvent event) {
        try {
            deliveryService.deliverMessage(event);
        } catch (Exception e) {
            log.warn("[chat-realtime] after-commit message delivery failed. roomId={}",
                    event.message().getRoomId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReadCommitted(ChatReadCommittedEvent event) {
        try {
            deliveryService.deliverRead(event);
        } catch (Exception e) {
            log.warn("[chat-realtime] after-commit read delivery failed. roomId={}",
                    event.readEvent().getRoomId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRoomClosedCommitted(ChatRoomClosedCommittedEvent event) {
        try {
            deliveryService.deliverRoomClosed(event);
        } catch (Exception e) {
            log.warn("[chat-realtime] after-commit room-close delivery failed. roomId={}",
                    event.closedEvent().roomId(), e);
        }
    }
}
