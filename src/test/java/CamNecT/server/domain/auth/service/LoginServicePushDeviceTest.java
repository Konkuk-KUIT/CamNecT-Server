package CamNecT.server.domain.auth.service;

import CamNecT.server.domain.auth.dto.others.WithdrawRequest;
import CamNecT.server.domain.profile.components.certificate.repository.CertificateRepository;
import CamNecT.server.domain.profile.components.education.repository.EducationRepository;
import CamNecT.server.domain.profile.components.experience.repository.ExperienceRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.verification.email.repository.EmailVerificationTokenRepository;
import CamNecT.server.global.jwt.service.TokenSessionService;
import CamNecT.server.global.notification.service.PushDeviceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServicePushDeviceTest {

    @Mock
    TokenSessionService tokenSessionService;

    @Mock
    PushDeviceService pushDeviceService;

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    CertificateRepository certificateRepository;

    @Mock
    EducationRepository educationRepository;

    @Mock
    ExperienceRepository experienceRepository;

    @Mock
    EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    UserProfileRepository userProfileRepository;

    @InjectMocks
    LoginService loginService;

    @Test
    void logoutDisablesOnlyRequestedPushDeviceBeforeRevokingSession() {
        loginService.logout(1L, "session-id", "device-id");

        InOrder inOrder = inOrder(pushDeviceService, tokenSessionService);
        inOrder.verify(pushDeviceService).disableForUserAndDevice(1L, "device-id");
        inOrder.verify(tokenSessionService).revokeSession(1L, "session-id");
    }

    @Test
    void legacyLogoutWithoutDeviceIdDisablesAllPushDevices() {
        loginService.logout(1L, "session-id", null);

        InOrder inOrder = inOrder(pushDeviceService, tokenSessionService);
        inOrder.verify(pushDeviceService).disableAllForUser(1L);
        inOrder.verify(tokenSessionService).revokeSession(1L, "session-id");
    }

    @Test
    void withdrawDisablesPushDevicesBeforeRevokingSession() {
        Users user = Users.builder()
                .userId(1L)
                .passwordHash("encoded")
                .status(UserStatus.ACTIVE)
                .build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded")).thenReturn(true);

        loginService.withdraw(1L, new WithdrawRequest("password"));

        InOrder inOrder = inOrder(pushDeviceService, tokenSessionService);
        inOrder.verify(pushDeviceService).disableAllForUser(1L);
        inOrder.verify(tokenSessionService).revokeAll(1L);
    }
}
