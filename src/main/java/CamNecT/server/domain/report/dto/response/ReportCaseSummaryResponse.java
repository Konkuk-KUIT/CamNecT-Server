package CamNecT.server.domain.report.dto.response;

import CamNecT.server.domain.report.model.*;

import java.time.LocalDateTime;

public record ReportCaseSummaryResponse(
        Long caseId,
        String targetKey,
        ReportTargetUserResponse targetAuthor,
        Long targetId,
        TargetType targetType,
        long reportCount,
        ReportStatus status,
        ReportCategory decidedCategory,
        PenaltyType appliedPenalty,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReportCaseSummaryResponse from(ReportCase reportCase) {
        return new ReportCaseSummaryResponse(
                reportCase.getCaseId(),
                reportCase.getTargetKey(),
                ReportTargetUserResponse.from(reportCase.getReportedUser()),
                reportCase.getTargetId(),
                reportCase.getTargetType(),
                reportCase.getReportCount(),
                reportCase.getStatus(),
                reportCase.getDecidedCategory(),
                reportCase.getAppliedPenalty(),
                reportCase.getCreatedAt(),
                reportCase.getUpdatedAt()
        );
    }
}
