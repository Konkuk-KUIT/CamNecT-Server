package CamNecT.server.global.notification.event;

import CamNecT.server.global.notification.model.NotificationType;

public record NewChatMessageEvent(
        Long receiverUserId,
        Long actorUserId,
        Long roomId,
        String content
) implements NotifiableEvent {

    @Override
    public NotificationType type() {
        return NotificationType.CHAT_MESSAGE_RECEIVED;
    }

    @Override
    public Long requestId() {
        return roomId; // 채팅방 ID를 requestId 필드에 저장 (필요 시 활용)
    }

    @Override
    public String message() {
        if (content == null) return "사진/동영상";

        int limit = 40; // 최대 표시 글자수 제한
        if (content.codePointCount(0, content.length()) > limit) {
            int endIndex = content.offsetByCodePoints(0, limit);
            return content.substring(0, endIndex) + "...";
        }
        return content;
    }

    @Override public Long roomId() { return roomId; }
}
