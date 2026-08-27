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
import CamNecT.server.global.jwt.service.TokenSessionService;
import CamNecT.server.global.jwt.util.JwtFacade;
import CamNecT.server.global.jwt.util.JwtUtil;
import CamNecT.server.global.notification.service.PushDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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
    private final TokenSessionService tokenSessionService;
    private final PushDeviceService pushDeviceService;

    @Transactional
    public LoginResponse login(LoginRequest req) {
        Users user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new CustomException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        if (userReportPenaltyService.hasActiveRestriction(user.getUserId())
                || user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new CustomException(AuthErrorCode.USER_WITHDRAWN);
        }

        if (user.getRole() == UserRole.ADMIN) {
            return issueTokenLoginResponse(user, LoginNextStep.ADMIN_DASHBOARD);
        }

        DocumentVerificationSubmission latest = submissionRepo
                .findTopByUserIdOrderBySubmittedAtDesc(user.getUserId())
                .orElse(null);
        LoginNextStep nextStep = resolveNext(user, latest);

        if (needsVerificationToken(nextStep)) {
            String verification = jwtUtil.generateVerificationToken(user.getUserId(), user.getRole());
            return new LoginResponse(
                    "Bearer",
                    verification,
                    null,
                    jwtUtil.getVerificationTokenExpirationMs(),
                    0L,
                    user.getUserId(),
                    user.getStatus().name(),
                    user.getRole().name(),
                    nextStep
            );
        }

        return issueTokenLoginResponse(user, nextStep);
    }

    public VerificationCompleteResponse getVerificationCompleteInfo(Long userId) {
        Users user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_PROFILE_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE || profile.isInitialSetupCompleted()) {
            throw new CustomException(AuthErrorCode.INITIAL_SETUP_NOT_ALLOWED);
        }

        String institutionName = profile.getInstitutionId() == null
                ? null
                : institutionRepository.findNameKorById(profile.getInstitutionId()).orElse(null);
        String majorName = profile.getMajorId() == null
                ? null
                : majorRepository.findNameKorById(profile.getMajorId()).orElse(null);

        return new VerificationCompleteResponse(
                user.getName(),
                profile.getStudentNo(),
                institutionName,
                majorName
        );
    }

    @Transactional
    public void logout(Long userId, String sessionId, String deviceId) {
        if (deviceId == null) {
            pushDeviceService.disableAllForUser(userId);
        } else {
            pushDeviceService.disableForUserAndDevice(userId, deviceId);
        }
        tokenSessionService.revokeSession(userId, sessionId);
    }

    @Transactional
    public void withdraw(Long userId, WithdrawRequest req) {
        Users user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new CustomException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        certificateRepository.deleteByUser_UserId(userId);
        educationRepository.deleteByUser_UserId(userId);
        experienceRepository.deleteByUser_UserId(userId);
        emailVerificationTokenRepository.deleteByUser_UserId(userId);
        userProfileRepository.deleteByUserId(userId);

        String suffix = userId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        user.withdrawAnonymize(
                "탈퇴한 사용자",
                "deleted_" + suffix,
                null,
                null,
                UserStatus.WITHDRAWN
        );
        userRepository.save(user);
        pushDeviceService.disableAllForUser(userId);
        tokenSessionService.revokeAll(userId);
    }

    private LoginResponse issueTokenLoginResponse(Users user, LoginNextStep nextStep) {
        String sessionId = UUID.randomUUID().toString();
        String access = jwtFacade.createAccessToken(user, sessionId);
        String refresh = jwtFacade.createRefreshToken(user, sessionId);
        tokenSessionService.create(user.getUserId(), access, refresh);

        return new LoginResponse(
                "Bearer",
                access,
                refresh,
                jwtUtil.getAccessTokenExpirationMs(),
                jwtUtil.getRefreshTokenExpirationMs(),
                user.getUserId(),
                user.getStatus().name(),
                user.getRole().name(),
                nextStep
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

        if (user.getStatus() == UserStatus.ADMIN_PENDING) {
            if (latest == null) {
                return LoginNextStep.DOCUMENT_REQUIRED;
            }
            return switch (latest.getStatus()) {
                case REJECTED, CANCELED -> LoginNextStep.DOCUMENT_REQUIRED;
                case PENDING -> LoginNextStep.DOCUMENT_REVIEW_WAITING;
                case APPROVED -> LoginNextStep.VERIFICATION_COMPLETE;
            };
        }
        return LoginNextStep.HOME;
    }

    private boolean needsVerificationToken(LoginNextStep nextStep) {
        return nextStep == LoginNextStep.DOCUMENT_REQUIRED
                || nextStep == LoginNextStep.DOCUMENT_REVIEW_WAITING;
    }
}
