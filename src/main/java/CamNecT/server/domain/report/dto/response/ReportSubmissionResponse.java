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
        String evidenceImageUrl,
        LocalDateTime createdAt
) {
    public static ReportSubmissionResponse from(Report report, String evidenceImageUrl) {
        return new ReportSubmissionResponse(
                report.getReportId(),
                report.getReporterId(),
                report.getReportCategory(),
                report.getTitle(),
                report.getContext(),
                evidenceImageUrl,
                report.getCreatedAt()
        );
    }
}
