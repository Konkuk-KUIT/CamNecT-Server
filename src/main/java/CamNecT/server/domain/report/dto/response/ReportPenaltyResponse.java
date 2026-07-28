package CamNecT.server.domain.report.dto.response;

import CamNecT.server.domain.report.model.PenaltyType;
import CamNecT.server.domain.report.model.UserReportPenalty;

import java.time.LocalDateTime;

public record ReportPenaltyResponse(
        Long penaltyId,
        Long caseId,
        String targetKey,
        PenaltyType penaltyType,
        LocalDateTime suspensionEndDate,
        String reason,
        boolean active,
        LocalDateTime createdAt
) {
    public static ReportPenaltyResponse from(UserReportPenalty penalty, LocalDateTime now) {
        return new ReportPenaltyResponse(
                penalty.getPenaltyId(),
                penalty.getReportCase().getCaseId(),
                penalty.getReportCase().getTargetKey(),
                penalty.getPenaltyType(),
                penalty.getSuspensionEndDate(),
                penalty.getReason(),
                penalty.isActiveAt(now),
                penalty.getCreatedAt()
        );
    }
}
