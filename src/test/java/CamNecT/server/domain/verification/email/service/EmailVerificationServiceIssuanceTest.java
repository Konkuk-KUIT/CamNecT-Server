package CamNecT.server.domain.verification.email.service;

import CamNecT.server.domain.auth.dto.signup.VerifySignupEmailRequest;
import CamNecT.server.domain.auth.dto.signup.VerifySignupEmailResponse;
import CamNecT.server.domain.auth.service.PasswordService;
import CamNecT.server.domain.auth.service.SignupService;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.verification.email.event.EmailVerificationCodeIssuedEvent;
import CamNecT.server.domain.verification.email.model.EmailVerificationLockBucket;
import CamNecT.server.domain.verification.email.model.EmailVerificationToken;
import CamNecT.server.domain.verification.email.repository.EmailVerificationLockBucketRepository;
import CamNecT.server.domain.verification.email.repository.EmailVerificationTokenRepository;
import CamNecT.server.global.jwt.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceIssuanceTest {

    private static final String EMAIL = "User@Example.com";
    private static final String ACTIVE_EMAIL = "user@example.com";
    private static final String USERNAME = "user";
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
        ReflectionTestUtils.setField(service, "expirationMinutes", 30L);
        ReflectionTestUtils.setField(service, "resendCooldownSeconds", 60L);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void signupResendWithinCooldownKeepsCurrentCodeAndResponseContract() {
        short bucketId = stubBucket(EMAIL);
        EmailVerificationToken latest = EmailVerificationToken.issueForEmail(
                EMAIL,
                CODE,
                30,
                LocalDateTime.now(CLOCK).minusSeconds(30)
        );
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(tokenRepository.findByActiveEmail(ACTIVE_EMAIL)).thenReturn(Optional.of(latest));

        long expiresMinutes = service.sendSignupCode(EMAIL);

        assertThat(expiresMinutes).isEqualTo(30L);
        InOrder order = inOrder(lockBucketRepository, userRepository, tokenRepository);
        order.verify(lockBucketRepository).findByIdForUpdate(bucketId);
        order.verify(userRepository).existsByEmail(EMAIL);
        order.verify(tokenRepository).findByActiveEmail(ACTIVE_EMAIL);
        verify(tokenRepository, never()).deleteByActiveEmail(ACTIVE_EMAIL);
        verify(tokenRepository, never()).save(any());
        verify(applicationEventPublisher, never())
                .publishEvent(any(EmailVerificationCodeIssuedEvent.class));
    }

    @Test
    void signupResendAtCooldownBoundaryIssuesOneReplacement() {
        stubBucket(EMAIL);
        EmailVerificationToken latest = EmailVerificationToken.issueForEmail(
                EMAIL,
                CODE,
                30,
                LocalDateTime.now(CLOCK).minusSeconds(60)
        );
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(tokenRepository.findByActiveEmail(ACTIVE_EMAIL)).thenReturn(Optional.of(latest));
        when(tokenRepository.deleteByActiveEmail(ACTIVE_EMAIL)).thenReturn(1);
        when(tokenRepository.save(any(EmailVerificationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.sendSignupCode(EMAIL);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        EmailVerificationToken issued = tokenCaptor.getValue();
        assertThat(issued.getEmail()).isEqualTo(EMAIL);
        assertThat(issued.getActiveEmail()).isEqualTo(ACTIVE_EMAIL);
        assertThat(issued.getCreatedAt()).isEqualTo(LocalDateTime.now(CLOCK));

        ArgumentCaptor<EmailVerificationCodeIssuedEvent> eventCaptor =
                ArgumentCaptor.forClass(EmailVerificationCodeIssuedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().email()).isEqualTo(EMAIL);
        assertThat(issued.matchesCode(eventCaptor.getValue().code())).isTrue();
    }

    @Test
    void expiredTokenBypassesCooldown() {
        stubBucket(EMAIL);
        EmailVerificationToken activeToken = EmailVerificationToken.issueForEmail(
                EMAIL,
                CODE,
                0,
                LocalDateTime.now(CLOCK).minusSeconds(30)
        );
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(tokenRepository.findByActiveEmail(ACTIVE_EMAIL)).thenReturn(Optional.of(activeToken));
        when(tokenRepository.save(any(EmailVerificationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.sendSignupCode(EMAIL);

        verify(tokenRepository).deleteByActiveEmail(ACTIVE_EMAIL);
        verify(tokenRepository).save(any(EmailVerificationToken.class));
        verify(applicationEventPublisher).publishEvent(any(EmailVerificationCodeIssuedEvent.class));
    }

    @Test
    void lockedTokenCannotBypassTheIssuanceCooldown() {
        stubBucket(EMAIL);
        EmailVerificationToken locked = EmailVerificationToken.issueForEmail(
                EMAIL,
                CODE,
                30,
                LocalDateTime.now(CLOCK).minusSeconds(30)
        );
        for (int attempt = 0; attempt < 5; attempt++) {
            locked.increaseAttempt();
        }
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(tokenRepository.findByActiveEmail(ACTIVE_EMAIL)).thenReturn(Optional.of(locked));

        assertThat(service.sendSignupCode(EMAIL)).isEqualTo(30L);

        verify(tokenRepository, never()).deleteByActiveEmail(ACTIVE_EMAIL);
        verify(tokenRepository, never()).save(any());
        verify(applicationEventPublisher, never())
                .publishEvent(any(EmailVerificationCodeIssuedEvent.class));
    }

    @Test
    void passwordResetIssuanceLocksLatestUserBeforeEmailBucket() {
        Users user = activeUser();
        when(userRepository.existsByUsername(USERNAME)).thenReturn(true);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);
        when(userRepository.findUserIdByUsername(USERNAME)).thenReturn(Optional.of(user.getUserId()));
        when(userRepository.findByIdForUpdate(user.getUserId())).thenReturn(Optional.of(user));
        short bucketId = stubBucket(EMAIL);
        when(tokenRepository.findByActiveEmail(ACTIVE_EMAIL)).thenReturn(Optional.empty());
        when(tokenRepository.save(any(EmailVerificationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.sendPasswordResetCode(USERNAME, EMAIL);

        InOrder order = inOrder(userRepository, lockBucketRepository, tokenRepository);
        order.verify(userRepository).findByIdForUpdate(user.getUserId());
        order.verify(lockBucketRepository).findByIdForUpdate(bucketId);
        order.verify(tokenRepository).findByActiveEmail(ACTIVE_EMAIL);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getUser()).isSameAs(user);
    }

    @Test
    void signupVerificationLocksEmailBeforeRecheckingExistingUser() {
        Users existing = activeUser();
        short bucketId = stubBucket(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        VerifySignupEmailResponse response = service.verifySignupAndCreateUser(signupRequest());

        assertThat(response.alreadyVerified()).isTrue();
        InOrder order = inOrder(lockBucketRepository, userRepository);
        order.verify(lockBucketRepository).findByIdForUpdate(bucketId);
        order.verify(userRepository).findByEmail(EMAIL);
        verify(tokenRepository, never()).findByActiveEmailForUpdate(ACTIVE_EMAIL);
    }

    private short stubBucket(String email) {
        short bucketId = bucketFor(email);
        when(lockBucketRepository.findByIdForUpdate(bucketId)).thenReturn(Optional.of(
                EmailVerificationLockBucket.builder().bucketId(bucketId).build()
        ));
        return bucketId;
    }

    private short bucketFor(String email) {
        return (short) Math.floorMod(
                email.trim().toLowerCase(Locale.ROOT).hashCode(),
                64
        );
    }

    private Users activeUser() {
        return Users.builder()
                .userId(1L)
                .username(USERNAME)
                .email(EMAIL)
                .passwordHash("password-hash")
                .name("user")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private VerifySignupEmailRequest signupRequest() {
        return new VerifySignupEmailRequest(
                EMAIL,
                CODE,
                USERNAME,
                "password1",
                "user",
                "01012345678",
                new VerifySignupEmailRequest.Agreements(true, true)
        );
    }

}
