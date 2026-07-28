package CamNecT.server.domain.report.dto.response;

import CamNecT.server.domain.report.model.Report;
import CamNecT.server.domain.report.model.ReportCategory;

import java.time.LocalDateTime;

public record ReportSubmissionResponse(
        Long reportId,
        Long reporterId,
        ReportCategory submittedCategory,
        String title,
        String context,
        boolean hasEvidence,
        LocalDateTime createdAt
) {
    public static ReportSubmissionResponse from(Report report) {
        return new ReportSubmissionResponse(
                report.getReportId(),
                report.getReporterId(),
                report.getReportCategory(),
                report.getTitle(),
                report.getContext(),
                report.getEvidenceImageUrl() != null && !report.getEvidenceImageUrl().isBlank(),
                report.getCreatedAt()
        );
    }
}
