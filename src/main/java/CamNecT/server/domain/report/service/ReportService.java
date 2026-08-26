package CamNecT.server.domain.report.service;

import CamNecT.server.domain.activity.service.ActivityService;
import CamNecT.server.domain.activity.service.RecruitmentService;
import CamNecT.server.domain.community.service.CommentService;
import CamNecT.server.domain.community.service.PostService;
import CamNecT.server.domain.report.dto.request.ReportCreateRequest;
import CamNecT.server.domain.report.dto.request.ReportProcessRequest;
import CamNecT.server.domain.report.dto.response.ReportCaseDetailResponse;
import CamNecT.server.domain.report.dto.response.ReportCaseSummaryResponse;
import CamNecT.server.domain.report.dto.response.ReportEvidenceResponse;
import CamNecT.server.domain.report.dto.response.ReportPenaltyResponse;
import CamNecT.server.domain.report.dto.response.ReportSubmissionResponse;
import CamNecT.server.domain.report.model.*;
import CamNecT.server.domain.report.repository.ReportCaseRepository;
import CamNecT.server.domain.report.repository.ReportEvidenceRepository;
import CamNecT.server.domain.report.repository.ReportRepository;
import CamNecT.server.domain.report.repository.UserReportPenaltyRepository;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.BaseErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.ActivityErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.ReportErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import CamNecT.server.global.storage.dto.response.PresignDownloadResponse;
import CamNecT.server.global.storage.service.PresignEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.exception.ConstraintViolationException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportService {

    private static final String DUPLICATE_REPORT_CONSTRAINT = "uk_report_reporter_case_slot";
    private static final String TARGET_INTEGRITY_QUARANTINE_PREFIX = "[TARGET_INTEGRITY_QUARANTINED]";

    private final ReportRepository reportRepository;
    private final ReportEvidenceRepository evidenceRepository;
    private final ReportCaseRepository reportCaseRepository;
    private final UserReportPenaltyRepository penaltyRepository;
    private final UserRepository userRepository;
    private final PresignEngine presignEngine;
    private final ReportAttachmentService reportAttachmentService;
    private final PostService postService;
    private final CommentService commentService;
    private final ActivityService activityService;
    private final RecruitmentService recruitmentService;
    private final UserReportPenaltyService userReportPenaltyService;
    private final ReportTargetResolver reportTargetResolver;
    private final Clock clock;

    // 관리자 검증 공통 메서드
    private void validateAdmin(Long userId) {
        Users adminUser = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        if (adminUser.getRole() != UserRole.ADMIN) throw new CustomException(UserErrorCode.USER_NOT_ADMIN);
    }

    /**
     * 1. 신고 접수
     */
    @Transactional
    public Long createReport(Long reporterId, ReportCreateRequest dto) {
        List<String> evidenceKeys = dto.evidenceImageKeys() == null ? List.of() : dto.evidenceImageKeys();
        ReportTargetResolver.ResolvedTarget target = reportTargetResolver.resolve(reporterId, dto);
        if (reporterId.equals(target.author().getUserId())) {
            throw new CustomException(ReportErrorCode.REPORT_SELF_NOT_ALLOWED);
        }

        userRepository.lockUserRow(target.author().getUserId());
        Users targetAuthor = userRepository.findById(target.author().getUserId())
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        ReportCase reportCase = reportCaseRepository.findByTargetKey(target.targetKey())
                .orElseGet(() -> reportCaseRepository.saveAndFlush(ReportCase.open(
                        target.targetKey(),
                        targetAuthor,
                        target.targetId(),
                        dto.postType()
                )));

        validateExistingCase(reportCase, target, dto.postType());
        rejectQuarantinedCase(reportCase);

        if (reportCase.getStatus() != ReportStatus.RECEIVED) {
            throw new CustomException(ReportErrorCode.REPORT_CASE_CLOSED);
        }

        if (reportRepository.existsByReporterIdAndReportCase_CaseId(reporterId, reportCase.getCaseId())) {
            throw new CustomException(ReportErrorCode.REPORT_DUPLICATE);
        }

        Report report = new Report(
                reportCase,
                reporterId,
                targetAuthor.getUserId(),
                dto.postType() == TargetType.USER ? null : target.targetId(),
                dto.postType(),
                dto.reportCategory(),
                dto.title(),
                dto.context()
        );
        Report savedReport;
        try {
            savedReport = reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateReportSubmission(e)) {
                throw new CustomException(ReportErrorCode.REPORT_DUPLICATE, e);
            }
            throw e;
        }

        reportCase.addReport();
        
        // 증거 이미지가 있으면 최종 경로로 이동 (consume)
        if (!evidenceKeys.isEmpty()) {
            List<ReportEvidence> evidence = reportAttachmentService.applyOnReportCreate(
                    reporterId,
                    savedReport,
                    evidenceKeys
            );
        }
        
        return savedReport.getReportId();
    }

    private boolean isDuplicateReportSubmission(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && DUPLICATE_REPORT_CONSTRAINT.equalsIgnoreCase(constraintViolation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 2. 관리자용 목록 조회
     */
    public Page<ReportCaseSummaryResponse> findAllReports(Long userId, TargetType type, ReportStatus status, Pageable pageable) {
        validateAdmin(userId);
        return reportCaseRepository.findAllByFilters(type, status, pageable)
                .map(ReportCaseSummaryResponse::from);
    }

    /**
     * 3. 관리자 신고 처리 (승인/반려)
     */
    @Transactional
    public void processReport(Long userId, Long caseId, ReportProcessRequest request) {
        validateAdmin(userId);

        ReportStatus newStatus = request.status();
        if (newStatus != ReportStatus.RESOLVED && newStatus != ReportStatus.REJECTED) {
            throw new CustomException(ReportErrorCode.REPORT_INVALID_STATUS);
        }
        if (newStatus == ReportStatus.RESOLVED && request.decidedCategory() == null) {
            throw new CustomException(ReportErrorCode.REPORT_CATEGORY_REQUIRED);
        }

        ReportCase caseSnapshot = reportCaseRepository.findById(caseId)
                .orElseThrow(() -> new CustomException(ReportErrorCode.REPORT_NOT_FOUND));
        userRepository.lockUserRow(caseSnapshot.getReportedUser().getUserId());
        ReportCase reportCase = reportCaseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new CustomException(ReportErrorCode.REPORT_NOT_FOUND));

        if (reportCase.getStatus() != ReportStatus.RECEIVED) {
            throw new CustomException(ReportErrorCode.REPORT_ALREADY_PROCESSED);
        }

        List<Report> submissions = reportRepository
                .findAllByReportCase_CaseIdOrderByCreatedAtAsc(caseId);
        LocalDateTime now = LocalDateTime.now(clock);

        if (newStatus == ReportStatus.RESOLVED) {
            rejectQuarantinedCase(reportCase);
            reportTargetResolver.validateStoredCase(reportCase, submissions);
            reportCase.decideCategory(request.decidedCategory());
            PenaltyType penaltyType = userReportPenaltyService.applyPenalty(reportCase);
            deleteReportedContent(userId, reportCase);
            reportCase.resolve(userId, request.decidedCategory(), penaltyType, request.reason(), now);
            submissions.forEach(report -> {
                report.applyPenalty(penaltyType);
                report.updateStatus(ReportStatus.RESOLVED);
            });
            return;
        }

        reportCase.reject(userId, request.reason(), now);
        submissions.forEach(report -> report.updateStatus(ReportStatus.REJECTED));
    }

    private void validateExistingCase(
            ReportCase reportCase,
            ReportTargetResolver.ResolvedTarget target,
            TargetType targetType
    ) {
        if (!target.targetKey().equals(reportCase.getTargetKey())
                || !target.targetId().equals(reportCase.getTargetId())
                || targetType != reportCase.getTargetType()
                || !target.author().getUserId().equals(reportCase.getReportedUser().getUserId())) {
            throw new CustomException(ReportErrorCode.REPORT_TARGET_INTEGRITY_UNVERIFIED);
        }
    }

    private void rejectQuarantinedCase(ReportCase reportCase) {
        String moderationReason = reportCase.getModerationReason();
        if (moderationReason != null && moderationReason.startsWith(TARGET_INTEGRITY_QUARANTINE_PREFIX)) {
            throw new CustomException(ReportErrorCode.REPORT_TARGET_INTEGRITY_UNVERIFIED);
        }
    }

    /**
     * 신고된 게시글 삭제
     */
    protected void deleteReportedContent(Long adminId, ReportCase reportCase) {
        if (reportCase.getTargetType() == TargetType.USER) {
            return;
        }

        Long postId = reportCase.getTargetId();
        TargetType targetType = reportCase.getTargetType();

        try {
            switch (targetType) {
                case COMMUNITY -> postService.deleteForModeration(adminId, postId);
                case COMMUNITY_COMMENT -> commentService.deleteForModeration(adminId, postId);
                case ACTIVITY -> activityService.delete(postId, adminId);
                case ACTIVITY_RECRUITMENT -> recruitmentService.deleteRecruitment(adminId, postId);
                case USER -> log.info("Skipping account deletion in report processing. caseId={}", reportCase.getCaseId());
                case CHAT -> log.info("Skipping chat deletion in report processing. caseId={}", reportCase.getCaseId());
            }
        } catch (CustomException e) {
            if (isIgnorableDeletionFailure(e.getErrorCode())) {
                log.warn(
                        "Reported content already missing. continue report process. caseId={}, targetType={}, postId={}, code={}",
                        reportCase.getCaseId(), targetType, postId, e.getErrorCode().getCode()
                );
                return;
            }

            log.error(
                    "Failed to delete reported content. caseId={}, targetType={}, postId={}, code={}",
                    reportCase.getCaseId(), targetType, postId, e.getErrorCode().getCode(), e
            );
            throw e;
        }
    }

    private boolean isIgnorableDeletionFailure(BaseErrorCode errorCode) {
        return errorCode == CommunityErrorCode.POST_NOT_FOUND
                || errorCode == CommunityErrorCode.COMMENT_NOT_FOUND
                || errorCode == ActivityErrorCode.ACTIVITY_NOT_FOUND
                || errorCode == ActivityErrorCode.RECRUITMENT_NOT_FOUND;
    }

    /**
     * 신고 상세 조회 (관리자용)
     */
    public ReportCaseDetailResponse getReportDetail(Long userId, Long caseId) {
        validateAdmin(userId);

        ReportCase reportCase = reportCaseRepository.findById(caseId)
                .orElseThrow(() -> new CustomException(ReportErrorCode.REPORT_NOT_FOUND));

        List<Report> reports = reportRepository.findAllByReportCase_CaseIdOrderByCreatedAtAsc(caseId);
        Map<Long, List<ReportEvidenceResponse>> evidenceByReportId = new HashMap<>();
        if (!reports.isEmpty()) {
            evidenceRepository.findAllByReport_ReportIdInOrderByReport_ReportIdAscSortOrderAsc(
                    reports.stream().map(Report::getReportId).toList()
            ).forEach(evidence -> evidenceByReportId
                    .computeIfAbsent(evidence.getReport().getReportId(), ignored -> new ArrayList<>())
                    .add(ReportEvidenceResponse.from(evidence)));
        }
        List<ReportSubmissionResponse> submissions = reports.stream()
                .map(report -> ReportSubmissionResponse.from(
                        report,
                        evidenceByReportId.getOrDefault(report.getReportId(), List.of())
                ))
                .toList();

        LocalDateTime now = LocalDateTime.now(clock);
        List<ReportPenaltyResponse> existingPenalties = penaltyRepository
                .findAllByUser_UserIdOrderByCreatedAtDesc(reportCase.getReportedUser().getUserId())
                .stream()
                .map(penalty -> ReportPenaltyResponse.from(penalty, now))
                .toList();

        return ReportCaseDetailResponse.from(reportCase, submissions, existingPenalties);
    }

    /**
     * 특정 유저의 신고 누적 수 조회
     */
    public long getResolvedReportCount(Long adminId, Long userId) {
        validateAdmin(adminId);
        return userReportPenaltyService.countPenalties(userId);
    }

    public PresignDownloadResponse getEvidenceDownloadUrl(
            Long adminId,
            Long caseId,
            Long reportId,
            Long evidenceId
    ) {
        validateAdmin(adminId);

        ReportEvidence evidence = evidenceRepository
                .findByEvidenceIdAndReport_ReportIdAndReport_ReportCase_CaseId(evidenceId, reportId, caseId)
                .orElseThrow(() -> new CustomException(ReportErrorCode.REPORT_EVIDENCE_NOT_FOUND));

        return presignEvidenceDownload(evidence);
    }

    private PresignDownloadResponse presignEvidenceDownload(ReportEvidence evidence) {
        return presignEngine.presignDownload(
                evidence.getStorageKey(),
                evidence.getOriginalFilename(),
                evidence.getContentType()
        );
    }

}
