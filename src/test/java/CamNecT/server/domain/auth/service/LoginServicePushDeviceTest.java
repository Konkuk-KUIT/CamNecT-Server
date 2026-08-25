package CamNecT.server.domain.auth.service;

import CamNecT.server.domain.auth.dto.LoginNextStep;
import CamNecT.server.domain.auth.dto.login.LoginRequest;
import CamNecT.server.domain.auth.dto.login.LoginResponse;
import CamNecT.server.domain.auth.dto.others.WithdrawRequest;
import CamNecT.server.domain.profile.components.certificate.repository.CertificateRepository;
import CamNecT.server.domain.profile.components.education.repository.EducationRepository;
import CamNecT.server.domain.profile.components.experience.repository.ExperienceRepository;
import CamNecT.server.domain.profile.components.institutions.repository.InstitutionRepository;
import CamNecT.server.domain.profile.components.majors.repository.MajorRepository;
import CamNecT.server.domain.report.service.UserReportPenaltyService;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserFollowRepository;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.users.repository.UserTagMapRepository;
import CamNecT.server.domain.verification.email.repository.EmailVerificationTokenRepository;
import CamNecT.server.domain.verification.document.repository.DocumentVerificationSubmissionRepository;
import CamNecT.server.global.jwt.service.TokenSessionService;
import CamNecT.server.global.jwt.util.JwtFacade;
import CamNecT.server.global.jwt.util.JwtUtil;
import CamNecT.server.global.notification.service.PushDeviceService;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LoginServicePushDeviceTest {

    @Mock
    TokenSessionService tokenSessionService;

    @Mock
    PushDeviceService pushDeviceService;

    @Mock
    UserRepository userRepository;

    @Mock
    JwtUtil jwtUtil;

    @Mock
    JwtFacade jwtFacade;

    @Mock
    UserReportPenaltyService userReportPenaltyService;

    @Mock
    DocumentVerificationSubmissionRepository submissionRepository;

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

    @Mock
    UserTagMapRepository userTagMapRepository;

    @Mock
    UserFollowRepository userFollowRepository;

    @Mock
    GlobalPresignMethods globalPresignMethods;

    @Mock
    InstitutionRepository institutionRepository;

    @Mock
    MajorRepository majorRepository;

    @InjectMocks
    LoginService loginService;

    @Test
    void loginUsesReadCommittedSoChecksAfterTheUserLockSeeCurrentState() throws NoSuchMethodException {
        Method login = LoginService.class.getMethod("login", LoginRequest.class);

        Transactional transactional = login.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    @Test
    void loginLocksLatestUserStateBeforeCheckingPasswordAndCreatingSession() {
        Users admin = Users.builder()
                .userId(1L)
                .username("admin")
                .passwordHash("encoded")
                .status(UserStatus.ACTIVE)
                .role(UserRole.ADMIN)
                .build();
        when(userRepository.findUserIdByUsername("admin")).thenReturn(Optional.of(1L));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
        when(jwtFacade.createAccessToken(org.mockito.ArgumentMatchers.eq(admin), anyString()))
                .thenReturn("access");
        when(jwtFacade.createRefreshToken(org.mockito.ArgumentMatchers.eq(admin), anyString()))
                .thenReturn("refresh");
        when(jwtUtil.getAccessTokenExpirationMs()).thenReturn(1_000L);
        when(jwtUtil.getRefreshTokenExpirationMs()).thenReturn(2_000L);

        LoginResponse response = loginService.login(new LoginRequest("admin", "password"));

        InOrder order = inOrder(userRepository, passwordEncoder, tokenSessionService);
        order.verify(userRepository).findUserIdByUsername("admin");
        order.verify(userRepository).findByIdForUpdate(1L);
        order.verify(passwordEncoder).matches("password", "encoded");
        order.verify(tokenSessionService).create(1L, "access", "refresh");
        assertThat(response.nextStep()).isEqualTo(LoginNextStep.ADMIN_DASHBOARD);
    }

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
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded")).thenReturn(true);
        when(userProfileRepository.findProfileImageKeyByUserId(1L))
                .thenReturn(Optional.of("profile/user-1/images/avatar.png"));

        loginService.withdraw(1L, new WithdrawRequest("password"));

        InOrder deletionOrder = inOrder(userRepository, experienceRepository);
        deletionOrder.verify(userRepository).findByIdForUpdate(1L);
        deletionOrder.verify(experienceRepository).deleteByUser_UserId(1L);

        InOrder inOrder = inOrder(pushDeviceService, tokenSessionService);
        inOrder.verify(pushDeviceService).disableAllForUser(1L);
        inOrder.verify(tokenSessionService).revokeAll(1L);
        verify(userTagMapRepository).deleteAllByUserId(1L);
        verify(userFollowRepository).deleteAllByUserId(1L);
        verify(globalPresignMethods).deleteAfterCommit(Set.of("profile/user-1/images/avatar.png"));
    }
}
