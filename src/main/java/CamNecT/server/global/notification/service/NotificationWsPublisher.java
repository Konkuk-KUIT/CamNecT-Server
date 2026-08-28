package CamNecT.server.global.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWsPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendToUser(Long userId, Object payload) {
        log.debug("[ws-notif] sendToUser userId={}, payload={}", userId, payload);
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/notifications", payload);
    } // FE는 /user/queue/notifications 구독
}
