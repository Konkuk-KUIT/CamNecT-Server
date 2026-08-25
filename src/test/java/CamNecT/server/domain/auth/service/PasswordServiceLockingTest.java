package CamNecT.server.domain.auth.service;

import CamNecT.server.domain.profile.dto.request.UpdatePasswordRequest;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.jwt.service.TokenSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PasswordServiceLockingTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TokenSessionService tokenSessionService = mock(TokenSessionService.class);
    private final PasswordService service =
            new PasswordService(userRepository, passwordEncoder, tokenSessionService);

    @Test
    void updateLocksUserBeforeChangingPassword() {
        Users user = Users.builder()
                .userId(1L)
                .passwordHash("old-hash")
                .status(UserStatus.ACTIVE)
                .build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldpass1", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newpass2")).thenReturn("new-hash");

        service.updateMyPassword(1L, new UpdatePasswordRequest("oldpass1", "newpass2"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepository).findByIdForUpdate(1L);
        verify(userRepository, never()).findById(1L);
        verify(tokenSessionService).revokeAll(1L);
    }

    @Test
    void resetRejectsUserWithdrawnWhileWaitingForTheLock() {
        Users user = Users.builder().userId(1L).status(UserStatus.WITHDRAWN).build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.resetPasswordByUserId(1L, "newpass2")
        );

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_WITHDRAWN);
        verifyNoInteractions(passwordEncoder, tokenSessionService);
    }
}
