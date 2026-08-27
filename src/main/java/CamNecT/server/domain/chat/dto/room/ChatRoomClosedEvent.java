package CamNecT.server.domain.chat.dto.room;

public record ChatRoomClosedEvent(
        String type,
        Long roomId
) {
    public static ChatRoomClosedEvent of(Long roomId) {
        return new ChatRoomClosedEvent("ROOM_CLOSED", roomId);
    }
}
