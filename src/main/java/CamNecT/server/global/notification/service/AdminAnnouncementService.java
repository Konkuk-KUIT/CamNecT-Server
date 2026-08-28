package CamNecT.server.global.notification.service;

import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.notification.dto.request.AdminAnnouncementRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static CamNecT.server.global.notification.dto.request.AdminAnnouncementRequest.TargetType.USERS;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAnnouncementService {

    private static final int BATCH_SIZE = 500;

    private final UserRepository userRepository;
    private final AdminAnnouncementBatchService adminAnnouncementBatchService;

    public long send(Long adminUserId, AdminAnnouncementRequest request) {
        validateAdmin(adminUserId);
        validate(request);

        if (request.targetType() == USERS) {
            List<Long> receiverIds = resolveSelectedUsers(request.targetUserIds());
            if (receiverIds.isEmpty()) return 0L;
            return adminAnnouncementBatchService.dispatch(adminUserId, request, receiverIds);
        }

        long total = 0L;
        long lastSeenUserId = 0L;

        while (true) {
            Slice<Long> result = userRepository.findUserIdsByStatusAndUserIdGreaterThan(
                    UserStatus.ACTIVE,
                    lastSeenUserId,
                    PageRequest.of(0, BATCH_SIZE)
            );
            if (result.isEmpty()) break;

            List<Long> receiverIds = result.getContent();
            total += adminAnnouncementBatchService.dispatch(
                    adminUserId,
                    request,
                    receiverIds
            );
            lastSeenUserId = receiverIds.get(receiverIds.size() - 1);

            if (!result.hasNext()) break;
        }

        log.info("[admin-announcement] sent by admin={}, total={}", adminUserId, total);
        return total;
    }

    private void validate(AdminAnnouncementRequest request) {
        if (request.targetType() == USERS &&
                (request.targetUserIds() == null || request.targetUserIds().isEmpty())) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        String link = request.link();
        if (link != null && !link.isBlank() && !isSafeInternalPath(link.trim())) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private boolean isSafeInternalPath(String link) {
        try {
            URI uri = new URI(link);
            String rawPath = uri.getRawPath();
            String decodedPath = uri.getPath();

            return !uri.isAbsolute()
                    && uri.getRawAuthority() == null
                    && rawPath != null
                    && rawPath.startsWith("/")
                    && !rawPath.startsWith("//")
                    && decodedPath != null
                    && !decodedPath.startsWith("//")
                    && !decodedPath.contains("\\");
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private void validateAdmin(Long adminUserId) {
        Users admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        if (admin.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
        if (admin.getRole() != UserRole.ADMIN) {
            throw new CustomException(UserErrorCode.USER_NOT_ADMIN);
        }
    }

    private List<Long> resolveSelectedUsers(List<Long> targetUserIds) {
        Set<Long> uniqueIds = new LinkedHashSet<>(targetUserIds);

        return userRepository.findAllById(uniqueIds).stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(Users::getUserId)
                .toList();
    }
}
