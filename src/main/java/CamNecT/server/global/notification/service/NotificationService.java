package CamNecT.server.global.notification.service;

import CamNecT.server.domain.users.model.UserProfile;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.NotificationErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.notification.dto.response.NotificationItemResponse;
import CamNecT.server.global.notification.model.Notification;
import CamNecT.server.global.notification.model.NotificationType;
import CamNecT.server.global.notification.repository.NotificationRepository;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static CamNecT.server.global.notification.util.NotificationUtil.titleOf;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_MESSAGE_LENGTH = 255;
    private static final int MAX_LINK_LENGTH = 500;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PublicUrlIssuer publicUrlIssuer;

    @Transactional
    public void create(Long receiverUserId,
                       Long actorUserId,
                       NotificationType type,
                       String message,
                       Long postId,
                       Long commentId) {
        create(receiverUserId, actorUserId, type, message, postId, commentId, null, null);
    }

    @Transactional
    public void create(Long receiverUserId,
                       Long actorUserId,
                       NotificationType type,
                       String message,
                       Long postId,
                       Long commentId,
                       Long requestId,
                       String link) {

        Notification n = Notification.of(
                receiverUserId, actorUserId, type,
                truncate(message, MAX_MESSAGE_LENGTH),
                postId, commentId, requestId,
                truncate(link, MAX_LINK_LENGTH)
        );
        notificationRepository.save(n);

        log.debug("[notif] saved (queued). receiver={}, requestId={}", receiverUserId, requestId);
    }

    @Transactional(readOnly = true)
    public Slice<Notification> list(Long receiverUserId, Long cursorId, int size) {
        requireAuthenticatedUser(receiverUserId);
        Pageable pageable = PageRequest.of(0, normalizeSize(size));

        NotificationType exclude = NotificationType.CHAT_MESSAGE_RECEIVED;

        if (cursorId == null) {
            return notificationRepository
                    .findByReceiverUserIdAndReadFalseAndTypeNotOrderByIdDesc(receiverUserId, exclude, pageable);
        }
        return notificationRepository
                .findByReceiverUserIdAndReadFalseAndTypeNotAndIdLessThanOrderByIdDesc(receiverUserId, exclude, cursorId, pageable);
    }

    @Transactional(readOnly = true)
    public Slice<NotificationItemResponse> listItems(Long receiverUserId, Long cursorId, int size) {
        Slice<Notification> slice = list(receiverUserId, cursorId, size);

        Set<Long> actorIds = slice.getContent().stream()
                .map(Notification::getActorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Users> userMap = actorIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(Users::getUserId, Function.identity()));

        Map<Long, UserProfile> profileMap = actorIds.isEmpty()
                ? Map.of()
                : userProfileRepository.findAllByUserIdIn(actorIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity()));

        final String defaultImg = "/images/default.png";

        return slice.map(n -> {
            Long actorId = n.getActorUserId();

            String actorName = "시스템";
            String actorImg = defaultImg;

            if (actorId != null) {
                Users u = userMap.get(actorId);
                if (u != null && u.getName() != null) actorName = u.getName();

                UserProfile p = profileMap.get(actorId);
                if (p != null && p.getProfileImageKey() != null && !p.getProfileImageKey().isBlank()) {
                    String issuedUrl = publicUrlIssuer.issuePublicUrl(p.getProfileImageKey());
                    if (issuedUrl != null && !issuedUrl.isBlank()) {
                        actorImg = issuedUrl;
                    }
                }
            }

            return new NotificationItemResponse(
                    n.getId(),
                    n.getType(),
                    titleOf(n.getType()),
                    n.getMessage(),
                    n.isRead(),
                    n.getActorUserId(),
                    actorName,
                    actorImg,
                    n.getPostId(),
                    n.getCommentId(),
                    n.getRequestId(),
                    n.getLink(),
                    n.getCreatedAt()
            );
        });
    }

    @Transactional(readOnly = true)
    public long countUnread(Long receiverUserId) {
        requireAuthenticatedUser(receiverUserId);
        return notificationRepository.countByReceiverUserIdAndReadFalseAndTypeNot(
                receiverUserId, NotificationType.CHAT_MESSAGE_RECEIVED);
    }

    @Transactional
    public void markRead(Long receiverUserId, Long notificationId) {
        requireAuthenticatedUser(receiverUserId);
        if (!notificationRepository.existsByIdAndReceiverUserId(notificationId, receiverUserId)) {
            throw new CustomException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        }

        notificationRepository.markRead(receiverUserId, notificationId);
    }

    @Transactional
    public int markAllRead(Long receiverUserId) {
        requireAuthenticatedUser(receiverUserId);
        return notificationRepository.markAllRead(receiverUserId);
    }

    private void requireAuthenticatedUser(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.codePointCount(0, value.length()) <= maxLength) return value;
        int endIndex = value.offsetByCodePoints(0, maxLength);
        return value.substring(0, endIndex);
    }

    private int normalizeSize(int size) {
        if (size < 1) return DEFAULT_PAGE_SIZE;
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
