package CamNecT.server.domain.chat.service;

import CamNecT.server.domain.chat.dto.room.ChatRoomListUpdateDto;
import CamNecT.server.domain.chat.event.ChatMessageCommittedEvent;
import CamNecT.server.domain.chat.event.ChatReadCommittedEvent;
import CamNecT.server.domain.chat.event.ChatRoomClosedCommittedEvent;
import CamNecT.server.domain.chat.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRealtimeDeliveryService {

    private final ChatRepository chatRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void deliverMessage(ChatMessageCommittedEvent event) {
        Long roomId = event.message().getRoomId();
        sendSafely("/sub/chat/room/" + roomId, event.message(), "message-room");

        try {
            long receiverRoomUnread = chatRepository
                    .countByRoom_IdAndReceiver_UserIdAndIsReadFalse(roomId, event.receiverId());
            long senderRoomUnread = chatRepository
                    .countByRoom_IdAndReceiver_UserIdAndIsReadFalse(roomId, event.senderId());
            long receiverTotalUnread = chatRepository.countVisibleUnreadByUserId(event.receiverId());
            long senderTotalUnread = chatRepository.countVisibleUnreadByUserId(event.senderId());

            ChatRoomListUpdateDto receiverUpdate = roomListUpdate(
                    event, receiverRoomUnread, receiverTotalUnread);
            ChatRoomListUpdateDto senderUpdate = roomListUpdate(
                    event, senderRoomUnread, senderTotalUnread);

            sendSafely("/sub/user/" + event.receiverId() + "/rooms",
                    receiverUpdate, "message-receiver-list");
            sendSafely("/sub/user/" + event.senderId() + "/rooms",
                    senderUpdate, "message-sender-list");
        } catch (Exception e) {
            log.warn("[chat-realtime] room-list payload query failed. roomId={}", roomId, e);
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void deliverRead(ChatReadCommittedEvent event) {
        Long roomId = event.readEvent().getRoomId();
        sendSafely("/sub/chat/room/" + roomId, event.readEvent(), "read-room");

        try {
            long totalUnread = chatRepository.countVisibleUnreadByUserId(event.readerId());
            ChatRoomListUpdateDto readerUpdate = ChatRoomListUpdateDto.builder()
                    .roomId(roomId)
                    .lastMessage(event.lastMessage())
                    .unreadCount(0L)
                    .time(event.lastMessageTime())
                    .totalUnreadCount(totalUnread)
                    .build();

            sendSafely("/sub/user/" + event.readerId() + "/rooms",
                    readerUpdate, "read-reader-list");
        } catch (Exception e) {
            log.warn("[chat-realtime] read room-list payload query failed. roomId={}, reader={}",
                    roomId, event.readerId(), e);
        }
    }

    public void deliverRoomClosed(ChatRoomClosedCommittedEvent event) {
        Long roomId = event.closedEvent().roomId();
        sendSafely("/sub/chat/room/" + roomId, event.closedEvent(), "room-close");
    }

    private ChatRoomListUpdateDto roomListUpdate(
            ChatMessageCommittedEvent event,
            long unreadCount,
            long totalUnreadCount
    ) {
        return ChatRoomListUpdateDto.builder()
                .roomId(event.message().getRoomId())
                .lastMessage(event.lastMessage())
                .unreadCount(unreadCount)
                .time(event.lastMessageTime())
                .totalUnreadCount(totalUnreadCount)
                .build();
    }

    private void sendSafely(String destination, Object payload, String operation) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.warn("[chat-realtime] delivery failed. operation={}, destination={}",
                    operation, destination, e);
        }
    }
}
