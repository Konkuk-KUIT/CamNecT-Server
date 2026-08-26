package CamNecT.server.domain.profile.components;

import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileComponentAccessGuardTest {

    @Mock AccountAccessGuard accountAccessGuard;

    @Test
    void updateAccessLoadsAndLocksTheUserRow() {
        Users active = Users.builder().userId(1L).build();
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(active);

        ProfileComponentAccessGuard guard = new ProfileComponentAccessGuard(accountAccessGuard);

        assertThat(guard.requireAuthenticatedUserForUpdate(1L)).isSameAs(active);
        verify(accountAccessGuard).requireAccessibleForUpdate(1L);
    }

    @Test
    void readAccessKeepsTheNonLockingAccountGuardPath() {
        Users active = Users.builder().userId(1L).build();
        when(accountAccessGuard.requireAccessible(1L)).thenReturn(active);

        ProfileComponentAccessGuard guard = new ProfileComponentAccessGuard(accountAccessGuard);

        assertThat(guard.requireAuthenticatedUser(1L)).isSameAs(active);
        verify(accountAccessGuard).requireAccessible(1L);
    }

    @Test
    void activePenaltyRejectionFromCommonGuardBlocksComponentMutation() {
        when(accountAccessGuard.requireAccessibleForUpdate(1L))
                .thenThrow(new CustomException(AuthErrorCode.USER_SUSPENDED));

        ProfileComponentAccessGuard guard = new ProfileComponentAccessGuard(accountAccessGuard);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> guard.requireAuthenticatedUserForUpdate(1L)
        );
        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_SUSPENDED);
    }
}
