package CamNecT.server.domain.verification.email.service;

import CamNecT.server.domain.auth.dto.password.ResetPasswordRequest;
import CamNecT.server.domain.auth.dto.password.VerifyPasswordResetEmailRequest;
import CamNecT.server.domain.auth.dto.password.VerifyPasswordResetEmailResponse;
import CamNecT.server.domain.auth.dto.signup.VerifySignupEmailRequest;
import CamNecT.server.domain.auth.dto.signup.VerifySignupEmailResponse;
import CamNecT.server.domain.auth.service.PasswordService;
import CamNecT.server.domain.auth.service.SignupService;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.verification.email.event.EmailVerificationCodeIssuedEvent;
import CamNecT.server.domain.verification.email.model.EmailTokenUtil;
import CamNecT.server.domain.verification.email.model.EmailVerificationToken;
import CamNecT.server.domain.verification.email.repository.EmailVerificationLockBucketRepository;
import CamNecT.server.domain.verification.email.repository.EmailVerificationTokenRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.exception.InvalidPropertiesException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.VerificationErrorCode;
import CamNecT.server.global.jwt.model.TokenType;
import CamNecT.server.global.jwt.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final int MAX_USERNAME_LENGTH = 50;
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int EMAIL_LOCK_BUCKET_COUNT = 64;

    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailVerificationLockBucketRepository lockBucketRepository;
    private final UserRepository userRepository;

    private final SignupService signupService;
    private final PasswordService passwordService;

    private final JwtUtil jwtUtil;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Value("${app.auth.email-verification.expiration-minutes:30}")
    private long expirationMinutes;

    @Value("${app.auth.email-verification.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;


    /**
     * 1차: 이메일만 받고 코드 발급/발송
     * - email 기준으로 미사용 토큰 정리 후 최신 1개만 유지
     */
    @Transactional
    public long sendSignupCode(String email) {
        String normalizedEmail = normalize(email);
        lockEmailBucket(normalizedEmail);

        // 이미 가입된 이메일이면 막기(정책)
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new CustomException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        return issueCodeUnlessCoolingDown(normalizedEmail, null);
    }

    @Transactional
    public long sendPasswordResetCode(String username, String email) {
        String normalizedUsername = normalize(username);
        String normalizedEmail = normalize(email);

        List<String> invalidProperties = new ArrayList<>();
        if (normalizedUsername.isBlank() || normalizedUsername.length() > MAX_USERNAME_LENGTH
                || !userRepository.existsByUsername(normalizedUsername)) {
            invalidProperties.add("username");
        }
        if (normalizedEmail.isBlank() || normalizedEmail.length() > MAX_EMAIL_LENGTH
                || !userRepository.existsByEmail(normalizedEmail)) {
            invalidProperties.add("email");
        }
        if (!invalidProperties.isEmpty()) {
            throw new InvalidPropertiesException(invalidProperties);
        }

        Long userId = userRepository.findUserIdByUsername(normalizedUsername)
                .orElseThrow(() -> new InvalidPropertiesException(List.of("username")));
        Users user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new InvalidPropertiesException(List.of("username")));

        if (!emailsEqual(normalizedEmail, user.getEmail())) {
            throw new InvalidPropertiesException(List.of("email"));
        }

        validateRecoverableUser(user);
        String authoritativeEmail = user.getEmail();
        lockEmailBucket(authoritativeEmail);

        return issueCodeUnlessCoolingDown(authoritativeEmail, user);
    }

    public VerifyPasswordResetEmailResponse verifyPasswordResetEmail(VerifyPasswordResetEmailRequest req) {
        // 실패 횟수는 커밋하고, API 예외는 트랜잭션 종료 후 발생시킨다.
        VerificationTransactionResult<VerifyPasswordResetEmailResponse> result = transactionTemplate.execute(status -> {
            Long userId = userRepository.findUserIdByEmail(req.email())
                    .orElseThrow(() -> new CustomException(AuthErrorCode.USER_NOT_FOUND));
            Users user = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new CustomException(AuthErrorCode.USER_NOT_FOUND));

            validateRecoverableUser(user);
            String authoritativeEmail = user.getEmail();
            lockEmailBucket(authoritativeEmail);

            EmailVerificationToken token = tokenRepository
                    .findByActiveEmailForUpdate(canonicalEmail(authoritativeEmail))
                    .orElseThrow(() -> new CustomException(VerificationErrorCode.NO_ACTIVE_CODE));

            LocalDateTime now = LocalDateTime.now(clock);
            if (token.isExpired(now)) throw new CustomException(VerificationErrorCode.CODE_EXPIRED_OR_USED);
            if (token.isLocked()) throw new CustomException(VerificationErrorCode.TOO_MANY_ATTEMPTS);

            if (!token.matchesCode(req.code())) {
                token.increaseAttempt();
                return VerificationTransactionResult.failure(token.isLocked()
                        ? VerificationErrorCode.TOO_MANY_ATTEMPTS
                        : VerificationErrorCode.INVALID_CODE);
            }

            token.markUsed(now);
            token.linkUser(user);

            String resetToken = jwtUtil.generatePasswordResetToken(
                    user.getUserId(), user.getRole(), user.getPasswordHash());
            long expiresMinutes = jwtUtil.getVerificationTokenExpirationMs() / 60000L;

            return VerificationTransactionResult.success(
                    new VerifyPasswordResetEmailResponse(resetToken, expiresMinutes)
            );
        });

        return unwrap(result);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        jwtUtil.validateOrThrow(req.resetToken());
        if (jwtUtil.getTokenType(req.resetToken()) != TokenType.PASSWORD_RESET) {
            throw new CustomException(AuthErrorCode.TOKEN_TYPE_NOT_ALLOWED);
        }

        Long userId = jwtUtil.getUserId(req.resetToken());
        String passwordFingerprint = jwtUtil.getPasswordFingerprint(req.resetToken());
        passwordService.resetPasswordByUserId(userId, req.newPassword(), passwordFingerprint);
    }

    public VerifySignupEmailResponse verifySignupAndCreateUser(VerifySignupEmailRequest req) {
        // 실패 횟수는 커밋하고, 회원가입 예외는 정상 롤백되도록 트랜잭션 밖으로 전달한다.
        VerificationTransactionResult<VerifySignupEmailResponse> result = transactionTemplate.execute(status -> {
            String normalizedEmail = normalize(req.email());
            lockEmailBucket(normalizedEmail);

            // 인증 성공 후에만 사용자를 생성하므로, 같은 이메일의 사용자가 있으면 이미 가입 완료된 상태다.
            Users existing = userRepository.findByEmail(normalizedEmail).orElse(null);
            if (existing != null) {
                return VerificationTransactionResult.success(
                        new VerifySignupEmailResponse(existing.getUserId(), true, null, 0L)
                );
            }

            EmailVerificationToken token = tokenRepository
                    .findByActiveEmailForUpdate(canonicalEmail(normalizedEmail))
                    .orElseThrow(() -> new CustomException(VerificationErrorCode.NO_ACTIVE_CODE));

            LocalDateTime now = LocalDateTime.now(clock);
            if (token.isExpired(now)) throw new CustomException(VerificationErrorCode.CODE_EXPIRED_OR_USED);
            if (token.isLocked()) throw new CustomException(VerificationErrorCode.TOO_MANY_ATTEMPTS);

            if (!token.matchesCode(req.code())) {
                token.increaseAttempt();
                return VerificationTransactionResult.failure(token.isLocked()
                        ? VerificationErrorCode.TOO_MANY_ATTEMPTS
                        : VerificationErrorCode.INVALID_CODE);
            }

            Users user = signupService.signupVerifiedUser(req);

            // 토큰 사용 처리 + user 연결
            token.markUsed(now);
            token.linkUser(user);

            // 임시 토큰 발급(기존 로직 유지)
            String verificationToken = jwtUtil.generateVerificationToken(
                    user.getUserId(), user.getRole(), user.getPasswordHash());
            long expiresMinutes = jwtUtil.getVerificationTokenExpirationMs() / 60000L;

            return VerificationTransactionResult.success(
                    new VerifySignupEmailResponse(user.getUserId(), false, verificationToken, expiresMinutes)
            );
        });

        return unwrap(result);
    }

    private long issueCodeUnlessCoolingDown(String email, Users tokenOwner) {
        LocalDateTime now = LocalDateTime.now(clock);
        String activeEmail = canonicalEmail(email);
        EmailVerificationToken activeToken = tokenRepository.findByActiveEmail(activeEmail).orElse(null);
        if (activeToken != null
                && !activeToken.isExpired(now)
                && isCoolingDown(activeToken, now)) {
            if (tokenOwner != null) {
                activeToken.linkUser(tokenOwner);
            }
            // Keep the existing accepted-response contract without invalidating or re-mailing the current code.
            return expirationMinutes;
        }

        // JPQL bulk delete executes before the IDENTITY insert, so the active-email unique guard cannot
        // observe the superseded token and the replacement at the same time.
        tokenRepository.deleteByActiveEmail(activeEmail);

        String rawCode = EmailTokenUtil.new6DigitCode();
        EmailVerificationToken token = EmailVerificationToken.issueForEmail(
                email,
                rawCode,
                expirationMinutes,
                now
        );
        if (tokenOwner != null) {
            token.linkUser(tokenOwner);
        }
        tokenRepository.save(token);

        applicationEventPublisher.publishEvent(
                new EmailVerificationCodeIssuedEvent(email, rawCode, expirationMinutes)
        );

        return expirationMinutes;
    }

    private boolean isCoolingDown(EmailVerificationToken token, LocalDateTime now) {
        return resendCooldownSeconds > 0
                && token.getCreatedAt() != null
                && token.getCreatedAt().plusSeconds(resendCooldownSeconds).isAfter(now);
    }

    private void lockEmailBucket(String email) {
        short bucketId = (short) Math.floorMod(
                canonicalEmail(email).hashCode(),
                EMAIL_LOCK_BUCKET_COUNT
        );
        lockBucketRepository.findByIdForUpdate(bucketId)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing email verification lock bucket: " + bucketId));
    }

    private <T> T unwrap(VerificationTransactionResult<T> result) {
        if (result == null) {
            throw new IllegalStateException("Email verification transaction returned no result");
        }
        if (result.errorCode() != null) {
            throw new CustomException(result.errorCode());
        }
        return result.value();
    }

    private record VerificationTransactionResult<T>(T value, VerificationErrorCode errorCode) {
        private static <T> VerificationTransactionResult<T> success(T value) {
            return new VerificationTransactionResult<>(value, null);
        }

        private static <T> VerificationTransactionResult<T> failure(VerificationErrorCode errorCode) {
            return new VerificationTransactionResult<>(null, errorCode);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String canonicalEmail(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }

    private boolean emailsEqual(String left, String right) {
        return canonicalEmail(left).equals(canonicalEmail(right));
    }

    private void validateRecoverableUser(Users user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new CustomException(AuthErrorCode.USER_WITHDRAWN);
        }
    }
}
