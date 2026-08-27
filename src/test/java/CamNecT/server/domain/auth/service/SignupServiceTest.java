package CamNecT.server.domain.auth.service;

import CamNecT.server.domain.auth.dto.signup.VerifySignupEmailRequest;
import CamNecT.server.domain.users.model.UserProfile;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignupServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserProfileRepository userProfileRepository = mock(UserProfileRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final PasswordService passwordService = mock(PasswordService.class);
    private SignupService signupService;

    @BeforeEach
    void setUp() {
        signupService = new SignupService(
                userRepository,
                userProfileRepository,
                passwordEncoder,
                passwordService
        );
    }

    @Test
    void rejectsSignupUnlessBothRequiredAgreementsAreTrue() {
        CustomException missingPrivacyTerms = assertThrows(CustomException.class,
                () -> signupService.signupVerifiedUser(request(true, false)));
        CustomException missingServiceTerms = assertThrows(CustomException.class,
                () -> signupService.signupVerifiedUser(request(false, true)));

        assertThat(missingPrivacyTerms.getErrorCode()).isEqualTo(AuthErrorCode.TERMS_REQUIRED);
        assertThat(missingServiceTerms.getErrorCode()).isEqualTo(AuthErrorCode.TERMS_REQUIRED);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createsAdminPendingUserWhenBothAgreementsAreTrue() {
        VerifySignupEmailRequest request = request(true, true);
        when(passwordEncoder.encode("password123!A")).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(Users.class))).thenAnswer(invocation -> {
            Users user = invocation.getArgument(0);
            return Users.builder()
                    .userId(1L)
                    .email(user.getEmail())
                    .username(user.getUsername())
                    .name(user.getName())
                    .phoneNum(user.getPhoneNum())
                    .passwordHash(user.getPasswordHash())
                    .status(user.getStatus())
                    .build();
        });

        Users saved = signupService.signupVerifiedUser(request);

        assertThat(saved.getStatus()).isEqualTo(UserStatus.ADMIN_PENDING);
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void translatesDatabaseConstraintViolationToDuplicateResource() {
        VerifySignupEmailRequest request = request(true, true);
        when(passwordEncoder.encode("password123!A")).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(Users.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        CustomException exception = assertThrows(CustomException.class,
                () -> signupService.signupVerifiedUser(request));

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.DUPLICATE_RESOURCE);
        assertThat(exception.getCause()).isInstanceOf(DataIntegrityViolationException.class);
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    private VerifySignupEmailRequest request(boolean serviceTerms, boolean privacyTerms) {
        return new VerifySignupEmailRequest(
                "user@example.com",
                "123456",
                "username",
                "password123!A",
                "name",
                "01012345678",
                new VerifySignupEmailRequest.Agreements(serviceTerms, privacyTerms)
        );
    }
}
