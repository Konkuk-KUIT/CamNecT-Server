package CamNecT.server.global.common.auth;

import CamNecT.server.domain.report.service.UserReportPenaltyService;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.jwt.service.TokenSessionService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountAccessGuardTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserReportPenaltyService userReportPenaltyService = mock(UserReportPenaltyService.class);
    private final TokenSessionService tokenSessionService = mock(TokenSessionService.class);
    private final AccountAccessGuard guard = new AccountAccessGuard(
            userRepository,
            userReportPenaltyService,
            tokenSessionService
    );

    @Test
    void allowsAccessibleAccount() {
        Users user = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(guard.requireAccessible(1L)).isSameAs(user);
    }

    @Test
    void updateAccessUsesTheLockedLatestAccountState() {
        Users user = Users.builder().userId(1L).status(UserStatus.WITHDRAWN).build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        CustomException exception = assertThrows(CustomException.class,
                () -> guard.requireAccessibleForUpdate(1L));

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_WITHDRAWN);
        verify(userRepository).findByIdForUpdate(1L);
        verify(userRepository, never()).findById(1L);
        verifyNoInteractions(userReportPenaltyService);
    }

    @Test
    void snapshotAccessChecksStateWithoutLoadingAnEntity() {
        when(userRepository.findStatusByUserId(1L)).thenReturn(Optional.of(UserStatus.ACTIVE));

        guard.requireAccessibleSnapshot(1L);

        verify(userRepository).findStatusByUserId(1L);
        verify(userRepository, never()).findById(1L);
        verify(userRepository, never()).findByIdForUpdate(1L);
        verify(userReportPenaltyService).hasActiveRestriction(1L, UserStatus.ACTIVE);
    }

    @Test
    void updateAccessRechecksReportRestrictionAfterLockingTheUser() {
        Users user = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userReportPenaltyService.hasActiveRestriction(1L, UserStatus.ACTIVE)).thenReturn(true);

        CustomException exception = assertThrows(CustomException.class,
                () -> guard.requireAccessibleForUpdate(1L));

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_SUSPENDED);
        InOrder order = org.mockito.Mockito.inOrder(userRepository, userReportPenaltyService);
        order.verify(userRepository).findByIdForUpdate(1L);
        order.verify(userReportPenaltyService).hasActiveRestriction(1L, UserStatus.ACTIVE);
        verify(tokenSessionService).revokeAll(1L);
    }

    @Test
    void rejectsSuspendedAccount() {
        Users user = Users.builder().userId(1L).status(UserStatus.SUSPENDED).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        CustomException exception = assertThrows(CustomException.class,
                () -> guard.requireAccessible(1L));

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_SUSPENDED);
        verify(tokenSessionService).revokeAll(1L);
    }

    @Test
    void rejectsActiveAccountWhileReportRestrictionIsActive() {
        Users user = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userReportPenaltyService.hasActiveRestriction(1L, UserStatus.ACTIVE)).thenReturn(true);

        CustomException exception = assertThrows(CustomException.class,
                () -> guard.requireAccessible(1L));

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_SUSPENDED);
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
