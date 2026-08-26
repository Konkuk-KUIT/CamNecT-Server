package CamNecT.server.domain.verification.email.service;

import CamNecT.server.domain.auth.dto.password.VerifyPasswordResetEmailRequest;
import CamNecT.server.domain.auth.dto.password.VerifyPasswordResetEmailResponse;
import CamNecT.server.domain.auth.service.PasswordService;
import CamNecT.server.domain.auth.service.SignupService;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.verification.email.model.EmailVerificationLockBucket;
import CamNecT.server.domain.verification.email.model.EmailVerificationToken;
import CamNecT.server.domain.verification.email.repository.EmailVerificationLockBucketRepository;
import CamNecT.server.domain.verification.email.repository.EmailVerificationTokenRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.jwt.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceLockingTest {

    private static final String EMAIL = "user@example.com";
    private static final String CODE = "123456";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-26T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock EmailVerificationTokenRepository tokenRepository;
    @Mock EmailVerificationLockBucketRepository lockBucketRepository;
    @Mock UserRepository userRepository;
    @Mock SignupService signupService;
    @Mock PasswordService passwordService;
    @Mock JwtUtil jwtUtil;
    @Mock ApplicationEventPublisher applicationEventPublisher;
    @Mock TransactionTemplate transactionTemplate;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(
                tokenRepository,
                lockBucketRepository,
                userRepository,
                signupService,
                passwordService,
                jwtUtil,
                applicationEventPublisher,
                transactionTemplate,
                CLOCK
        );
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void passwordResetVerificationLocksLatestUserBeforeEmailToken() {
        Users lockedUser = Users.builder()
                .userId(1L)
                .username("user")
                .email(EMAIL)
                .passwordHash("latest-password-hash")
                .name("user")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        EmailVerificationToken token = EmailVerificationToken.issueForEmail(
                EMAIL,
                CODE,
                30,
                LocalDateTime.now(CLOCK)
        );

        when(userRepository.findUserIdByEmail(EMAIL)).thenReturn(Optional.of(1L));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lockedUser));
        short bucketId = bucketFor(EMAIL);
        when(lockBucketRepository.findByIdForUpdate(bucketId)).thenReturn(Optional.of(
                EmailVerificationLockBucket.builder().bucketId(bucketId).build()
        ));
        when(tokenRepository.findByActiveEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(token));
        when(jwtUtil.generatePasswordResetToken(1L, UserRole.USER, "latest-password-hash"))
                .thenReturn("reset-token");
        when(jwtUtil.getVerificationTokenExpirationMs()).thenReturn(1_800_000L);

        VerifyPasswordResetEmailResponse response = service.verifyPasswordResetEmail(
                new VerifyPasswordResetEmailRequest(EMAIL, CODE)
        );

        InOrder order = inOrder(userRepository, lockBucketRepository, tokenRepository, jwtUtil);
        order.verify(userRepository).findUserIdByEmail(EMAIL);
        order.verify(userRepository).findByIdForUpdate(1L);
        order.verify(lockBucketRepository).findByIdForUpdate(bucketId);
        order.verify(tokenRepository).findByActiveEmailForUpdate(EMAIL);
        order.verify(jwtUtil).generatePasswordResetToken(1L, UserRole.USER, "latest-password-hash");
        verify(userRepository, never()).findByEmail(EMAIL);
        assertThat(response.resetToken()).isEqualTo("reset-token");
    }

    @Test
    void passwordResetVerificationRejectsLatestWithdrawnStateBeforeLockingToken() {
        Users withdrawnUser = Users.builder()
                .userId(1L)
                .username("deleted-user")
                .passwordHash("latest-password-hash")
                .name("withdrawn user")
                .role(UserRole.USER)
                .status(UserStatus.WITHDRAWN)
                .build();

        when(userRepository.findUserIdByEmail(EMAIL)).thenReturn(Optional.of(1L));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(withdrawnUser));

        assertThatThrownBy(() -> service.verifyPasswordResetEmail(
                new VerifyPasswordResetEmailRequest(EMAIL, CODE)
        )).isInstanceOfSatisfying(CustomException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.USER_WITHDRAWN));

        InOrder order = inOrder(userRepository);
        order.verify(userRepository).findUserIdByEmail(EMAIL);
        order.verify(userRepository).findByIdForUpdate(1L);
        verify(lockBucketRepository, never()).findByIdForUpdate(any());
        verify(tokenRepository, never()).findByActiveEmailForUpdate(EMAIL);
    }

    private short bucketFor(String email) {
        return (short) Math.floorMod(email.trim().toLowerCase(Locale.ROOT).hashCode(), 64);
    }
}
