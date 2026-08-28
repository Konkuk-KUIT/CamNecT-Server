package CamNecT.server.global.common.auth;

import CamNecT.server.domain.report.service.UserReportPenaltyService;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.jwt.service.TokenSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountAccessGuard {

    private final UserRepository userRepository;
    private final UserReportPenaltyService userReportPenaltyService;
    private final TokenSessionService tokenSessionService;

    @Transactional
    public Users requireAccessible(Long userId) {
        requireUserId(userId);
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));

        validateAccessible(userId, user.getStatus());
        return user;
    }

    @Transactional(readOnly = true)
    public void requireAccessibleSnapshot(Long userId) {
        requireUserId(userId);
        UserStatus status = userRepository.findStatusByUserId(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        validateAccessible(userId, status);
    }

    @Transactional
    public Users requireAccessibleForUpdate(Long userId) {
        requireUserId(userId);
        Users user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));

        validateAccessible(userId, user.getStatus());
        return user;
    }

    private void validateAccessible(Long userId, UserStatus status) {
        if (status == UserStatus.WITHDRAWN) {
            revokeSafely(userId);
            throw new CustomException(AuthErrorCode.USER_WITHDRAWN);
        }
        if (status == UserStatus.SUSPENDED
                || userReportPenaltyService.hasActiveRestriction(userId, status)) {
            revokeSafely(userId);
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    @Transactional
    public void requireActive(Long userId) {
        Users user = requireAccessible(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(AuthErrorCode.ACTIVE_ACCOUNT_REQUIRED);
        }
    }

    private void revokeSafely(Long userId) {
        try {
            tokenSessionService.revokeAll(userId);
        } catch (RuntimeException e) {
            log.error("Failed to revoke token session for inaccessible userId={}", userId, e);
        }
    }
}
