package CamNecT.server.domain.profile.components;

import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileComponentAccessGuardTest {

    @Mock UserRepository userRepository;

    @Test
    void updateAccessLoadsAndLocksTheUserRow() {
        Users active = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(active));

        ProfileComponentAccessGuard guard = new ProfileComponentAccessGuard(userRepository);

        assertThat(guard.requireAuthenticatedUserForUpdate(1L)).isSameAs(active);
        verify(userRepository).findByIdForUpdate(1L);
    }

    @Test
    void withdrawnUserCannotResumeProfileComponentMutationAfterWaitingForLock() {
        Users withdrawn = Users.builder().userId(1L).status(UserStatus.WITHDRAWN).build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(withdrawn));

        ProfileComponentAccessGuard guard = new ProfileComponentAccessGuard(userRepository);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> guard.requireAuthenticatedUserForUpdate(1L)
        );
        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_WITHDRAWN);
    }
}
