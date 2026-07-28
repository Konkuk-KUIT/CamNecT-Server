package CamNecT.server.domain.auth.service;

import CamNecT.server.domain.auth.dto.LoginNextStep;
import CamNecT.server.domain.auth.dto.login.LoginRequest;
import CamNecT.server.domain.auth.dto.login.LoginResponse;
import CamNecT.server.domain.auth.dto.login.VerificationCompleteResponse;
import CamNecT.server.domain.auth.dto.others.WithdrawRequest;
import CamNecT.server.domain.profile.components.certificate.repository.CertificateRepository;
import CamNecT.server.domain.profile.components.education.repository.EducationRepository;
import CamNecT.server.domain.profile.components.experience.repository.ExperienceRepository;
import CamNecT.server.domain.profile.components.institutions.repository.InstitutionRepository;
import CamNecT.server.domain.profile.components.majors.repository.MajorRepository;
import CamNecT.server.domain.report.service.UserReportPenaltyService;
import CamNecT.server.domain.users.model.UserProfile;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.verification.document.model.DocumentVerificationSubmission;
import CamNecT.server.domain.verification.document.repository.DocumentVerificationSubmissionRepository;
import CamNecT.server.domain.verification.email.repository.EmailVerificationTokenRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import CamNecT.server.global.jwt.model.UserRefreshToken;
import CamNecT.server.global.jwt.repository.UserRefreshTokenRepository;
import CamNecT.server.global.jwt.util.JwtFacade;
import CamNecT.server.global.jwt.util.JwtUtil;

import CamNecT.server.global.jwt.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtFacade jwtFacade;
    private final UserReportPenaltyService userReportPenaltyService;
    private final DocumentVerificationSubmissionRepository submissionRepo;
    private final UserProfileRepository userProfileRepository;
    private final InstitutionRepository institutionRepository;
    private final MajorRepository majorRepository;
    private final CertificateRepository certificateRepository;
    private final ExperienceRepository experienceRepository;
    private final EducationRepository educationRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final UserRefreshTokenRepository userRefreshTokenRepository;

    @Transactional
    public LoginResponse login(LoginRequest req) {

        Users user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new CustomException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        // 신고 제재 이력을 기준으로 만료 상태를 갱신하고 활성 제재를 검사한다.
        if (userReportPenaltyService.hasActiveRestriction(user.getUserId())) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }

        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new CustomException(AuthErrorCode.USER_WITHDRAWN);
        }

        // 1) 관리자
        if (user.getRole() == UserRole.ADMIN) {
            String access = jwtFacade.createAccessToken(user);
            String refresh = jwtFacade.createRefreshToken(user);

            upsertRefreshToken(user.getUserId(), refresh);

            return new LoginResponse(
                    "Bearer", access, refresh,
                    jwtUtil.getAccessTokenExpirationMs(),
                    jwtUtil.getRefreshTokenExpirationMs(),
                    user.getUserId(),
                    user.getStatus().name(),
                    user.getRole().name(),
                    LoginNextStep.ADMIN_DASHBOARD
            );
        }

        // 2) 최신 증명서 제출 조회
        DocumentVerificationSubmission latest = submissionRepo
                .findTopByUserIdOrderBySubmittedAtDesc(user.getUserId())
                .orElse(null);

        // 3) nextStep 결정
        LoginNextStep nextStep = resolveNext(user, latest);

        // 5) 토큰 선택
        if (needsVerificationToken(nextStep)) {
            String verification = jwtUtil.generateVerificationToken(user.getUserId(), user.getRole());
            return new LoginResponse(
                    "Bearer", verification, null,
                    jwtUtil.getVerificationTokenExpirationMs(),
                    0L,
                    user.getUserId(),
                    user.getStatus().name(),
                    user.getRole().name(),
                    nextStep
            );
        }

        String access = jwtFacade.createAccessToken(user);
        String refresh = jwtFacade.createRefreshToken(user);

        upsertRefreshToken(user.getUserId(), refresh);

        return new LoginResponse(
                "Bearer", access, refresh,
                jwtUtil.getAccessTokenExpirationMs(),
                jwtUtil.getRefreshTokenExpirationMs(),
                user.getUserId(),
                user.getStatus().name(),
                user.getRole().name(),
                nextStep
        );
    }

    public VerificationCompleteResponse getVerificationCompleteInfo(Long userId) {
        Users u = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        UserProfile p = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_PROFILE_NOT_FOUND));
        if (u.getStatus() != UserStatus.ACTIVE || p.isInitialSetupCompleted()) {
            throw new CustomException(AuthErrorCode.INITIAL_SETUP_NOT_ALLOWED);
        }

        String instName = (p.getInstitutionId() == null) ? null
                : institutionRepository.findNameKorById(p.getInstitutionId()).orElse(null);

        String majorName = (p.getMajorId() == null) ? null
                : majorRepository.findNameKorById(p.getMajorId()).orElse(null);

        return new VerificationCompleteResponse(
                u.getName(),
                p.getStudentNo(),
                instName,
                majorName
        );
    }

    private LoginNextStep resolveNext(Users user, DocumentVerificationSubmission latest) {
        if (user.getStatus() == UserStatus.ACTIVE) {
            UserProfile profile = userProfileRepository.findByUserId(user.getUserId())
                    .orElseThrow(() -> new CustomException(UserErrorCode.USER_PROFILE_NOT_FOUND));
            return profile.isInitialSetupCompleted()
                    ? LoginNextStep.HOME
                    : LoginNextStep.VERIFICATION_COMPLETE;
        }

        // ADMIN_PENDING 흐름
        if (user.getStatus() == UserStatus.ADMIN_PENDING) {
            if (latest == null) return LoginNextStep.DOCUMENT_REQUIRED;

            return switch (latest.getStatus()) {
                case REJECTED, CANCELED -> LoginNextStep.DOCUMENT_REQUIRED;
                case PENDING -> LoginNextStep.DOCUMENT_REVIEW_WAITING;
                case APPROVED -> LoginNextStep.VERIFICATION_COMPLETE;
            };
        }
        // 방어적 기본값
        return LoginNextStep.HOME;
    }

    private boolean needsVerificationToken(LoginNextStep nextStep) {
        return nextStep == LoginNextStep.DOCUMENT_REQUIRED
                || nextStep == LoginNextStep.DOCUMENT_REVIEW_WAITING;
    }

    public void logout(Long loginUserId) { userRefreshTokenRepository.deleteById(loginUserId); }

    @Transactional
    public void withdraw(Long userId, WithdrawRequest req) {
        Users user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new CustomException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        // 개인정보성 데이터 제거: 프로필/학력/경력/증명서/이메일토큰 등
        certificateRepository.deleteByUser_UserId(userId);
        educationRepository.deleteByUser_UserId(userId);
        experienceRepository.deleteByUser_UserId(userId);
        emailVerificationTokenRepository.deleteByUser_UserId(userId);
        userProfileRepository.deleteByUserId(userId);

        // 유저 자체는 유지하되 익명화 + 로그인 불가 상태로 전환
        String suffix = userId + "_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        user.withdrawAnonymize(
                "탈퇴한 사용자",
                "deleted_" + suffix,
                null,
                null,
                UserStatus.WITHDRAWN
        );

        // 저장
        userRepository.save(user);
        userRefreshTokenRepository.deleteById(userId);
    }

    private void upsertRefreshToken(Long userId, String refreshToken) {
        String hash = TokenUtil.sha256Hex(refreshToken);
        Instant expiresAt = jwtUtil.getExpiration(refreshToken);
        UserRefreshToken row = userRefreshTokenRepository.findByIdForUpdate(userId).orElse(null);
        if (row == null) {
            userRefreshTokenRepository.save(UserRefreshToken.builder()
                    .userId(userId)
                    .refreshTokenHash(hash)
                    .expiresAt(expiresAt)
                    .updatedAt(Instant.now())
                    .build());
            return;
        }
        row.rotate(hash, expiresAt); // rotate도 Instant 받도록
    }
}
