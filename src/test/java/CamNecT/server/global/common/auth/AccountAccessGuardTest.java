package CamNecT.server.global.common.auth;

import CamNecT.server.domain.report.service.UserReportPenaltyService;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountAccessGuardTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserReportPenaltyService userReportPenaltyService = mock(UserReportPenaltyService.class);
    private final AccountAccessGuard guard = new AccountAccessGuard(userRepository, userReportPenaltyService);

    @Test
    void allowsAccessibleAccount() {
        Users user = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(guard.requireAccessible(1L)).isSameAs(user);
    }

    @Test
    void rejectsSuspendedAccount() {
        Users user = Users.builder().userId(1L).status(UserStatus.SUSPENDED).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        CustomException exception = assertThrows(CustomException.class,
                () -> guard.requireAccessible(1L));

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_SUSPENDED);
        verify(userReportPenaltyService).refreshRestrictionStatus(1L);
    }

    @Test
    void allowsAccountAfterReportSuspensionExpires() {
        Users user = Users.builder().userId(1L).status(UserStatus.SUSPENDED).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userReportPenaltyService.refreshRestrictionStatus(1L)).thenAnswer(invocation -> {
            user.changeStatus(UserStatus.ACTIVE);
            return false;
        });

        assertThat(guard.requireAccessible(1L)).isSameAs(user);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void rejectsWithdrawnAccount() {
        Users user = Users.builder().userId(1L).status(UserStatus.WITHDRAWN).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        CustomException exception = assertThrows(CustomException.class,
                () -> guard.requireAccessible(1L));

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_WITHDRAWN);
    }

    @Test
    void rejectsAdminPendingAccountFromActiveOnlyFeatures() {
        Users user = Users.builder().userId(1L).status(UserStatus.ADMIN_PENDING).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        CustomException exception = assertThrows(CustomException.class,
                () -> guard.requireActive(1L));

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.ACTIVE_ACCOUNT_REQUIRED);
    }
}
