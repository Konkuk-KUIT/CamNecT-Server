package CamNecT.server.domain.verification.document.service;

import CamNecT.server.global.point.model.PointEvent;
import CamNecT.server.global.point.service.PointService;
import CamNecT.server.domain.users.model.UserProfile;
import CamNecT.server.domain.profile.components.institutions.repository.InstitutionRepository;
import CamNecT.server.domain.profile.components.majors.repository.MajorRepository;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.verification.document.dto.AdminDocumentVerificationDetailResponse;
import CamNecT.server.domain.verification.document.dto.AdminDocumentVerificationListItemResponse;
import CamNecT.server.domain.verification.document.dto.AdminReviewDocumentVerificationRequest;
import CamNecT.server.domain.verification.document.event.DocumentVerificationReviewedEvent;
import CamNecT.server.domain.verification.document.repository.DocumentVerificationSubmissionRepository;
import CamNecT.server.domain.verification.document.model.DocumentVerificationSubmission;
import CamNecT.server.domain.verification.document.model.VerificationStatus;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.VerificationErrorCode;
import CamNecT.server.global.storage.dto.response.PresignDownloadResponse;
import CamNecT.server.global.storage.service.PresignEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDocumentVerificationService {
    @Value("${app.point.reward.sign-up:300}")
    private int rewardSignup;

    private final DocumentVerificationSubmissionRepository submissionRepo;
    private final UserRepository usersRepository;
    private final UserProfileRepository userProfileRepository;
    private final InstitutionRepository institutionRepository;
    private final MajorRepository majorRepository;
    private final PresignEngine presignEngine;
    private final ApplicationEventPublisher eventPublisher;
    private final PointService pointService;

    @Transactional(readOnly = true)
    public Page<AdminDocumentVerificationListItemResponse> list(VerificationStatus status, Pageable pageable) {

        Page<DocumentVerificationSubmission> page =
                submissionRepo.findByStatusOrderBySubmittedAtDesc(status, pageable);

        List<Long> userIds = page.getContent().stream()
                .map(DocumentVerificationSubmission::getUserId)
                .distinct()
                .toList();

        Map<Long, Users> usersMap = usersRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(Users::getUserId, u -> u));

        return page.map(s -> {
            Users u = usersMap.get(s.getUserId());
            if (u == null) throw new CustomException(VerificationErrorCode.USER_NOT_FOUND);

            return new AdminDocumentVerificationListItemResponse(
                    s.getId(), s.getStatus(), s.getDocType(), s.getSubmittedAt(),
                    u.getUserId(), u.getUsername(), u.getPhoneNum()
            );
        });
    }

    @Transactional(readOnly = true)
    public AdminDocumentVerificationDetailResponse get(Long submissionId) {
        DocumentVerificationSubmission s = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new CustomException(VerificationErrorCode.SUBMISSION_NOT_FOUND));

        Users u = usersRepository.findById(s.getUserId())
                .orElseThrow(() -> new CustomException(VerificationErrorCode.USER_NOT_FOUND));

        UserProfile p = userProfileRepository.findByUserId(s.getUserId()).orElse(null);

        return new AdminDocumentVerificationDetailResponse(
                s.getId(),
                s.getStatus(),
                s.getDocType(),
                s.getSubmittedAt(),
                s.getReviewedAt(),
                s.getRejectReason(),

                u.getUserId(),
                u.getUsername(),
                u.getPhoneNum(),
                u.getName(),

                p != null ? p.getStudentNo() : null,
                p != null ? p.getYearLevel() : null,
                p != null ? p.getInstitutionId() : null,
                p != null ? p.getMajorId() : null,

                safeName(s.getOriginalFilename()),
                normalize(s.getContentType()),
                s.getSize()
        );
    }

    @Transactional
    public void review(Long adminId, Long submissionId, AdminReviewDocumentVerificationRequest req) {

        DocumentVerificationSubmission s = submissionRepo.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new CustomException(VerificationErrorCode.SUBMISSION_NOT_FOUND));

        if (s.getStatus() != VerificationStatus.PENDING) {
            throw new CustomException(VerificationErrorCode.ONLY_PENDING_CAN_REVIEW);
        }

        Users user = usersRepository.findByIdForUpdate(s.getUserId())
                .orElseThrow(() -> new CustomException(VerificationErrorCode.USER_NOT_FOUND));

        //APPROVE
        if (req.decision() == AdminReviewDocumentVerificationRequest.Decision.APPROVE) {
            if (user.getStatus() != UserStatus.ADMIN_PENDING) {
                throw new CustomException(VerificationErrorCode.ONLY_PENDING_CAN_REVIEW);
            }
            // 승인 시 관리자 입력값을 UserProfile에 반영
            applyProfileInfoForApprove(user.getUserId(), req);

            s.approve(adminId);
            user.changeStatus(UserStatus.ACTIVE);

            eventPublisher.publishEvent(new DocumentVerificationReviewedEvent(
                    user.getEmail(),
                    s.getDocType(),
                    AdminReviewDocumentVerificationRequest.Decision.APPROVE,
                    null
            ));

            Long receiverId = user.getUserId();
            if (receiverId != null) pointService.earnPoint(receiverId,rewardSignup, PointEvent.signup(receiverId));



            return;
        }

        // REJECT
        String reason = trimToNull(req.reason());
        if (reason == null) {
            throw new CustomException(VerificationErrorCode.REJECT_REASON_REQUIRED);
        }

        s.reject(adminId, reason);

        eventPublisher.publishEvent(new DocumentVerificationReviewedEvent(
                user.getEmail(),
                s.getDocType(),
                AdminReviewDocumentVerificationRequest.Decision.REJECT,
                reason
        ));
    }

    @Transactional(readOnly = true)
    public PresignDownloadResponse downloadUrl(Long submissionId) {

        DocumentVerificationSubmission s = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new CustomException(VerificationErrorCode.SUBMISSION_NOT_FOUND));

        if (!StringUtils.hasText(s.getStorageKey())) {
            throw new CustomException(VerificationErrorCode.FILE_NOT_FOUND);
        }

        String ct = normalize(s.getContentType());
        String name = safeName(s.getOriginalFilename());

        return presignEngine.presignDownload(s.getStorageKey(), name, ct);
    }

    private void applyProfileInfoForApprove(Long userId, AdminReviewDocumentVerificationRequest req) {

        String studentName = trimToNull(req.studentName());
        String studentNo = trimToNull(req.studentNo());
        Long institutionId = req.institutionId();
        Long majorId = req.majorId();

        if (studentNo == null || studentName == null || institutionId == null || majorId == null) {
            // 승인 버튼은 “관리자 입력값 채운 뒤에만 호출”이지만 서버에서도 방어
            throw new CustomException(VerificationErrorCode.APPROVE_FIELDS_REQUIRED); // 임시. 전용 에러코드 추천
        }

        if (!institutionRepository.existsById(institutionId)) {
            throw new CustomException(UserErrorCode.INSTITUTION_NOT_FOUND);
        }
        if (majorRepository.findByMajorIdAndInstitution_InstitutionId(majorId, institutionId).isEmpty()) {
            throw new CustomException(UserErrorCode.MAJOR_NOT_FOUND);
        }

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_PROFILE_NOT_FOUND));

        profile.applyVerifiedInfo(studentName, studentNo, institutionId, majorId);
        profile.requireInitialSetup();
    }

    private String safeName(String name) {
        return (name == null || name.isBlank()) ? "document" : name;
    }

    private String normalize(String ct) {
        return (ct == null) ? "" : ct.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}
