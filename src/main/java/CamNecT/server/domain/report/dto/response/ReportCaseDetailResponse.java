package CamNecT.server.domain.report.dto.response;

import CamNecT.server.domain.report.model.*;

import java.time.LocalDateTime;
import java.util.List;

public record ReportCaseDetailResponse(
        Long caseId,
        String targetKey,
        ReportTargetUserResponse targetAuthor,
        Long targetId,
        TargetType targetType,
        long reportCount,
        ReportStatus status,
        ReportCategory decidedCategory,
        PenaltyType appliedPenalty,
        String moderationReason,
        Long processedByAdminId,
        LocalDateTime processedAt,
        List<ReportSubmissionResponse> submissions,
        List<ReportPenaltyResponse> existingPenalties,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReportCaseDetailResponse from(
            ReportCase reportCase,
            List<ReportSubmissionResponse> submissions,
            List<ReportPenaltyResponse> existingPenalties
    ) {
        return new ReportCaseDetailResponse(
                reportCase.getCaseId(),
                reportCase.getTargetKey(),
                ReportTargetUserResponse.from(reportCase.getReportedUser()),
                reportCase.getTargetId(),
                reportCase.getTargetType(),
                reportCase.getReportCount(),
                reportCase.getStatus(),
                reportCase.getDecidedCategory(),
                reportCase.getAppliedPenalty(),
                reportCase.getModerationReason(),
                reportCase.getProcessedByAdminId(),
                reportCase.getProcessedAt(),
                submissions,
                existingPenalties,
                reportCase.getCreatedAt(),
                reportCase.getUpdatedAt()
        );
    }
}
