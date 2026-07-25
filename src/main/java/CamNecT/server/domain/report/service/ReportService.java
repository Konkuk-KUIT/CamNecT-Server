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
import CamNecT.server.domain.users.model.UserStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

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
        // 신고 대상 사용자 존재 확인
        Users reportedUser = userRepository.findById(dto.reportedUserId())
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

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
        Report savedReport = reportRepository.save(report);
        
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

        return reports.map(ReportResponse::from);
    }

    /**
     * 3. 관리자 신고 처리 (승인/반려)
     */
    @Transactional
    public void processReport(Long userId, Long reportId, ReportStatus newStatus) {
        validateAdmin(userId);

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ReportErrorCode.REPORT_NOT_FOUND));

        report.updateStatus(newStatus);

        if (newStatus == ReportStatus.RESOLVED) {
            applyPenalty(report);
            deleteReportedContent(userId, report);
        }
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
     * 만료된 정지 자동 해제
     * - 임시 정지(7일) 기간이 끝났으면 사용자 상태 복구
     * - 영구 차단은 유지
     */
    @Transactional
    public void clearExpiredSuspension(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        // 정지 상태가 아니면 처리할 것 없음
        if (!user.isSuspended()) {
            return;
        }

        // 영구 차단이면 유지 (해제 불가)
        if (user.isPermanentlyBanned()) {
            return;
        }

        // 임시 정지 기간이 만료됨 → 상태 복구
        user.clearSuspension();
        user.changeStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    /**
     * 신고 기반 패널티 적용 로직
     * - 1회 접수: 경고 알림
     * - 2회 접수: 7일 정지
     * - 3회 접수: 영구 차단
     * - 즉시 영구 제재: 성희롱, 포교, 명백한 문서 위조 (1회 적발)
     */
    @Transactional
    protected void applyPenalty(Report report) {
        Users reportedUser = userRepository.findById(report.getReportedUserId())
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

        // 영구 차단 대상 확인 (즉시 제재)
        if (report.getReportCategory().isImmediateBan()) {
            reportedUser.applyPermanentBan("즉시 제재 대상: " + report.getReportCategory().getDisplayName());
            report.applyPenalty(PenaltyType.PERMANENT_BAN);
            userRepository.save(reportedUser);
            return;
        }

        // 신고 누적 횟수 증가 및 패널티 결정
        reportedUser.incrementReportCount();
        int reportCount = reportedUser.getReportCount();

        PenaltyType penaltyType = determinePenalty(reportCount, reportedUser);

        report.applyPenalty(penaltyType);
        userRepository.save(reportedUser);
    }

    /**
     * 신고 누적 횟수에 따른 패널티 결정 및 적용
     */
    private PenaltyType determinePenalty(int reportCount, Users reportedUser) {
        if (reportCount == 1) {
            // 1회: 경고 알림만 발송
            return PenaltyType.WARNING;
            // TODO: 경고 알림 발송 로직
        } else if (reportCount == 2) {
            // 2회: 7일 정지
            LocalDateTime suspensionEndDate = LocalDateTime.now().plusDays(7);
            reportedUser.applySuspension(suspensionEndDate);
            // TODO: 7일 정지 알림 발송 로직
            return PenaltyType.SUSPENDED_7_DAYS;
        } else if (reportCount >= 3) {
            // 3회 이상: 영구 차단
            reportedUser.applyPermanentBan("신고 누적 3회로 인한 영구 차단");
            // TODO: 영구 차단 알림 발송 로직
            return PenaltyType.PERMANENT_BAN;
        }
        return PenaltyType.WARNING;
    }

    /**
     * 신고 상세 조회 (관리자용)
     */
    public ReportResponse getReportDetail(Long userId, Long reportId) {
        validateAdmin(userId);

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ReportErrorCode.REPORT_NOT_FOUND));

        return ReportResponse.from(report);
    }

    /**
     * 특정 유저의 신고 누적 수 조회
     */
    public long getResolvedReportCount(Long userId) {
        return reportRepository.countByReportedUserIdAndStatus(userId, ReportStatus.RESOLVED);
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
}