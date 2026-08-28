package CamNecT.server.global.notification.event;

import CamNecT.server.global.notification.dto.NotificationPushPayload;
import CamNecT.server.global.notification.model.NotificationType;
import CamNecT.server.global.notification.service.*;
import CamNecT.server.global.notification.util.FCMSender;
import CamNecT.server.global.notification.util.NotificationLinkResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

import static CamNecT.server.global.notification.util.NotificationUtil.titleOf;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final FCMSender fcmSender;
    private final PushDeviceService pushDeviceService;
    private final NotificationWsPublisher notificationWsPublisher;
    private final NotificationLinkResolver notificationLinkResolver;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void persist(NotifiableEvent e) {

        log.debug("[notif] persist(beforeCommit) receiver={}, actor={}, type={}",
                e.receiverUserId(), e.actorUserId(), e.type());

        if (!isDeliverable(e) || e.shouldSkipSelfNotification()) return;
        // 채팅 메시지는 채팅 자체의 unread 상태를 사용하며 알림함에서는 제외되므로 push 전용으로 처리한다.
        if (e.type() == NotificationType.CHAT_MESSAGE_RECEIVED) return;
        String link = notificationLinkResolver.resolveOrFallback(e);

        notificationService.create(
                e.receiverUserId(),
                e.actorUserId(),
                e.type(),
                e.message(),
                e.postId(),
                e.commentId(),
                e.requestId(),
                link
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void push(NotifiableEvent e) {

        log.debug("[notif] push(afterCommit) receiver={}, actor={}, type={}",
                e.receiverUserId(), e.actorUserId(), e.type());

        if (!isDeliverable(e) || e.shouldSkipSelfNotification()) return;

        String title = titleOf(e.type());
        String body = e.message();
        String link = notificationLinkResolver.resolveOrFallback(e);

        // 1) 웹/로컬 실시간 알림 (WebSocket user queue)
        var wsPayload = new NotificationPushPayload(
                e.type().name(),
                title,
                body,
                e.postId(),
                e.commentId(),
                e.requestId(),
                link
        );
        try {
            notificationWsPublisher.sendToUser(e.receiverUserId(), wsPayload);
        } catch (Exception ex) {
            log.warn("[notif] WebSocket send failed. receiver={}, type={}", e.receiverUserId(), e.type(), ex);
        }

        // 2) 모바일 푸시(FCM)
        try {
            var tokens = pushDeviceService.findEnabledTokens(e.receiverUserId());
            log.debug("[notif] fcm tokens receiver={}, size={}",
                    e.receiverUserId(),
                    tokens == null ? 0 : tokens.size()
            );
            if (tokens == null || tokens.isEmpty()) return;


            Map<String, String> data = new java.util.HashMap<>();
            data.put("type", e.type().name());
            data.put("title", title);                 // data로 title 전달 (중복제거 위해 추가)
            data.put("body", body == null ? "" : body); // data로 body 전달 (중복제거 위해 추가)
            data.put("link", link);
            if (e.postId() != null) data.put("postId", String.valueOf(e.postId()));
            if (e.commentId() != null) data.put("commentId", String.valueOf(e.commentId()));
            if (e.requestId() != null) data.put("requestId", String.valueOf(e.requestId()));
            if (e.roomId() != null) data.put("roomId", String.valueOf(e.roomId()));

            log.debug("[notif] fcm send start receiver={}, title={}, bodyLen={}, dataKeys={}",
                    e.receiverUserId(), title, body == null ? 0 : body.length(), data.keySet());
            FCMSender.SendResult result = fcmSender.sendToTokens(tokens, data); //이앞 title,body 지웠다.
            log.debug("[notif] fcm send done receiver={}, requested={}, success={}, failure={}, invalid={}",
                    e.receiverUserId(),
                    result.requested(), result.success(), result.failure(),
                    result.invalidTokens() == null ? 0 : result.invalidTokens().size()
            );
            if (result.invalidTokens() != null && !result.invalidTokens().isEmpty()) {
                pushDeviceService.disableTokens(result.invalidTokens());
            }
        } catch (Exception ex) {
            log.warn("[notif] FCM delivery failed. receiver={}, type={}", e.receiverUserId(), e.type(), ex);
        }
    }

    private boolean isDeliverable(NotifiableEvent e) {
        if (e == null || e.receiverUserId() == null || e.receiverUserId() <= 0
                || e.type() == null || e.message() == null || e.message().isBlank()) {
            log.warn("[notif] skip invalid event. event={}", e);
            return false;
        }
        return true;
    }
}
