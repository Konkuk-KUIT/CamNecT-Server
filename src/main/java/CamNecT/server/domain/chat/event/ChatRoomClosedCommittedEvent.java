package CamNecT.server.domain.chat.event;

import CamNecT.server.domain.chat.dto.room.ChatRoomClosedEvent;

public record ChatRoomClosedCommittedEvent(
        ChatRoomClosedEvent closedEvent
) {
    public ChatRoomClosedCommittedEvent(Long roomId) {
        this(ChatRoomClosedEvent.of(roomId));
    }
}
