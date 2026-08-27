package CamNecT.server.domain.report.dto.response;

import CamNecT.server.domain.report.model.Report;
import CamNecT.server.domain.report.model.ReportCategory;

import java.time.LocalDateTime;
import java.util.List;

public record ReportSubmissionResponse(
        Long reportId,
        Long reporterId,
        ReportCategory submittedCategory,
        String title,
        String context,
        boolean hasEvidence,
        int evidenceCount,
        List<ReportEvidenceResponse> evidence,
        LocalDateTime createdAt
) {
    public static ReportSubmissionResponse from(Report report, List<ReportEvidenceResponse> evidence) {
        return new ReportSubmissionResponse(
                report.getReportId(),
                report.getReporterId(),
                report.getReportCategory(),
                report.getTitle(),
                report.getContext(),
                !evidence.isEmpty(),
                evidence.size(),
                evidence,
                report.getCreatedAt()
        );
    }
}
