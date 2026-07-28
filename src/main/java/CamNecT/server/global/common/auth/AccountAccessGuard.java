package CamNecT.server.global.common.auth;

import CamNecT.server.domain.report.service.UserReportPenaltyService;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AccountAccessGuard {

    private final UserRepository userRepository;
    private final UserReportPenaltyService userReportPenaltyService;

    @Transactional
    public Users requireAccessible(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));

        if (user.getStatus() == UserStatus.SUSPENDED) {
            userReportPenaltyService.refreshRestrictionStatus(userId);
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new CustomException(AuthErrorCode.USER_WITHDRAWN);
        }
        return user;
    }

    @Transactional
    public void requireActive(Long userId) {
        Users user = requireAccessible(userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(AuthErrorCode.ACTIVE_ACCOUNT_REQUIRED);
        }
    }
}
