package CamNecT.server.global.websocket;

import CamNecT.server.domain.chat.service.ChatPresenceService;
import CamNecT.server.domain.chat.service.ChatService;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final ChatPresenceService presenceService;
    private final ChatService chatService;
    private final UserRepository userRepository;
    private final ChatSocketErrorMapper errorMapper;
    private final ChatSocketErrorPublisher errorPublisher;

    @EventListener
    public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();

        if (destination != null && destination.startsWith("/sub/chat/room/")) {
            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs == null) return;

            Object userIdObj = attrs.get("userId");
            if (userIdObj == null) return;

            Long userId = Long.valueOf(userIdObj.toString());
            Long roomId = extractRoomId(destination);
            String sessionId = accessor.getSessionId();
            String subscriptionId = accessor.getSubscriptionId();

            try {
                if (roomId == null) return;
                Users user = userRepository.findById(userId)
                        .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
                chatService.markAllAsRead(roomId, user);
                presenceService.enter(roomId, userId, sessionId, subscriptionId);
                log.info("👤 SUBSCRIBE (입장): userId={}, roomId={}", userId, roomId);
            } catch (Exception e) {
                log.warn("채팅방 구독 후 읽음 처리 실패. userId={}, roomId={}", userId, roomId, e);
                errorPublisher.sendToSession(
                        userId,
                        sessionId,
                        errorMapper.map(e, accessor, null)
                );
            }
        }
    }

    @EventListener
    public void handleSessionUnsubscribeEvent(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        presenceService.leaveSubscription(accessor.getSessionId(), accessor.getSubscriptionId());
    }

    @EventListener
    public void handleSessionDisconnectEvent(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        presenceService.leaveSession(accessor.getSessionId());
        log.info("❌ DISCONNECT (퇴장): sessionId={}", accessor.getSessionId());
    }

    private Long extractRoomId(String destination) {
        try {
            return Long.parseLong(destination.substring(destination.lastIndexOf("/") + 1));
        } catch (NumberFormatException | NullPointerException e) {
            log.error("Room ID 추출 실패: destination={}", destination);
            return null;
        }
    }
}
