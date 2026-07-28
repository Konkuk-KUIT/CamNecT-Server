package CamNecT.server.domain.report.service;

import CamNecT.server.domain.activity.service.ActivityService;
import CamNecT.server.domain.activity.service.RecruitmentService;
import CamNecT.server.domain.community.service.CommentService;
import CamNecT.server.domain.community.service.PostService;
import CamNecT.server.domain.report.dto.request.ReportCreateRequest;
import CamNecT.server.domain.report.dto.response.ReportResponse;
import CamNecT.server.domain.report.model.*;
import CamNecT.server.domain.report.repository.ReportRepository;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.BaseErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.ActivityErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.ReportErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PublicUrlIssuer publicUrlIssuer;
    private final ReportAttachmentService reportAttachmentService;
    private final PostService postService;
    private final CommentService commentService;
    private final ActivityService activityService;
    private final RecruitmentService recruitmentService;
    private final UserReportPenaltyService userReportPenaltyService;

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
        if (reporterId.equals(dto.reportedUserId())) {
            throw new CustomException(ReportErrorCode.REPORT_SELF_NOT_ALLOWED);
        }

        validateTarget(dto);

        userRepository.findById(dto.reportedUserId())
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        String targetKey = Report.targetKeyFor(
                dto.postType(),
                dto.reportedUserId(),
                dto.reportedPostId()
        );
        if (reportRepository.existsByReporterIdAndTargetKey(reporterId, targetKey)) {
            throw new CustomException(ReportErrorCode.REPORT_DUPLICATE);
        }

        // 증거 이미지는 일단 presign된 키로 저장, 나중에 consume 처리
        Report report = new Report(
                reporterId,
                dto.reportedUserId(),
                dto.reportedPostId(),
                dto.postType(),
                dto.reportCategory(),
                dto.title(),
                dto.context(),
                dto.evidenceImageUrl()
        );
        Report savedReport;
        try {
            savedReport = reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ReportErrorCode.REPORT_DUPLICATE);
        }
        
        // 증거 이미지가 있으면 최종 경로로 이동 (consume)
        if (StringUtils.hasText(dto.evidenceImageUrl())) {
            String finalEvidenceUrl = reportAttachmentService.applyOnReportCreate(
                    reporterId,
                    savedReport.getReportId(),
                    dto.evidenceImageUrl()
            );
            savedReport.updateEvidenceImageUrl(finalEvidenceUrl);
        }
        
        return savedReport.getReportId();
    }

    /**
     * 2. 관리자용 목록 조회
     */
    public Page<ReportResponse> findAllReports(Long userId, TargetType type, ReportStatus status, Pageable pageable) {
        validateAdmin(userId);

        Page<Report> reports;

        if (type != null && status != null) {
            reports = reportRepository.findAllByPostTypeAndStatus(type, status, pageable);
        } else if (status != null) {
            reports = reportRepository.findAllByStatus(status, pageable);
        } else {
            reports = reportRepository.findAll(pageable);
        }

        return reports.map(this::toResponseWithEvidenceUrl);
    }

    /**
     * 3. 관리자 신고 처리 (승인/반려)
     */
    @Transactional
    public void processReport(Long userId, Long reportId, ReportStatus newStatus) {
        validateAdmin(userId);

        if (newStatus != ReportStatus.RESOLVED && newStatus != ReportStatus.REJECTED) {
            throw new CustomException(ReportErrorCode.REPORT_INVALID_STATUS);
        }

        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new CustomException(ReportErrorCode.REPORT_NOT_FOUND));

        if (report.getStatus() != ReportStatus.RECEIVED) {
            throw new CustomException(ReportErrorCode.REPORT_ALREADY_PROCESSED);
        }

        if (newStatus == ReportStatus.RESOLVED) {
            PenaltyType penaltyType = userReportPenaltyService.applyPenalty(report);
            report.applyPenalty(penaltyType);
            deleteReportedContent(userId, report);
        }

        report.updateStatus(newStatus);
    }

    /**
     * 신고된 게시글 삭제
     */
    @Transactional
    protected void deleteReportedContent(Long adminId, Report report) {
        if (report.getReportedPostId() == null || report.getPostType() == null) {
            return;
        }

        Long postId = report.getReportedPostId();
        TargetType targetType = report.getPostType();

        try {
            switch (targetType) {
                case COMMUNITY -> postService.delete(adminId, postId);
                case COMMUNITY_COMMENT -> commentService.delete(adminId, postId);
                case ACTIVITY -> activityService.delete(postId, adminId);
                case ACTIVITY_RECRUITMENT -> recruitmentService.deleteRecruitment(adminId, postId);
                case USER -> log.info("Skipping account deletion in report processing. reportId={}", report.getReportId());
                case CHAT -> log.info("Skipping chat deletion in report processing. reportId={}", report.getReportId());
            }
        } catch (CustomException e) {
            if (isIgnorableDeletionFailure(e.getErrorCode())) {
                log.warn(
                        "Reported content already missing. continue report process. reportId={}, targetType={}, postId={}, code={}",
                        report.getReportId(), targetType, postId, e.getErrorCode().getCode()
                );
                return;
            }

            log.error(
                    "Failed to delete reported content. reportId={}, targetType={}, postId={}, code={}",
                    report.getReportId(), targetType, postId, e.getErrorCode().getCode(), e
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
    public ReportResponse getReportDetail(Long userId, Long reportId) {
        validateAdmin(userId);

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ReportErrorCode.REPORT_NOT_FOUND));

        return toResponseWithEvidenceUrl(report);
    }

    /**
     * 특정 유저의 신고 누적 수 조회
     */
    public long getResolvedReportCount(Long adminId, Long userId) {
        validateAdmin(adminId);
        return userReportPenaltyService.countPenalties(userId);
    }

    /**
     * 증거 이미지 저장 키로부터 유효한 공개 URL 발급
     * 이미지 확장자 유효성 확인 후 공개 URL 반환
     */
    public String issueEvidenceImageUrl(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            return null;
        }
        return publicUrlIssuer.issueImagePublicUrl(storageKey);
    }

    private ReportResponse toResponseWithEvidenceUrl(Report report) {
        String evidenceImageUrl = issueEvidenceImageUrl(report.getEvidenceImageUrl());
        return ReportResponse.from(report, evidenceImageUrl);
    }

    private void validateTarget(ReportCreateRequest dto) {
        if (dto.postType() == TargetType.USER) {
            if (dto.reportedPostId() != null) {
                throw new CustomException(ReportErrorCode.REPORT_INVALID_TARGET);
            }
            return;
        }

        if (dto.reportedPostId() == null) {
            throw new CustomException(ReportErrorCode.REPORT_INVALID_TARGET);
        }
    }
}
