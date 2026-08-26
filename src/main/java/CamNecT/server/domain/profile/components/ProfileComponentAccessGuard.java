package CamNecT.server.domain.profile.components;

import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileComponentAccessGuard {

    private final AccountAccessGuard accountAccessGuard;

    public Users requireAuthenticatedUser(Long userId) {
        return accountAccessGuard.requireAccessible(userId);
    }

    public Users requireAuthenticatedUserForUpdate(Long userId) {
        return accountAccessGuard.requireAccessibleForUpdate(userId);
    }
}
