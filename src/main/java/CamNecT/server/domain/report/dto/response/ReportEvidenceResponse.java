package CamNecT.server.domain.report.dto.response;

import CamNecT.server.domain.report.model.ReportEvidence;

public record ReportEvidenceResponse(
        Long evidenceId,
        String originalFilename,
        String contentType,
        Long fileSize,
        int sortOrder
) {
    public static ReportEvidenceResponse from(ReportEvidence evidence) {
        return new ReportEvidenceResponse(
                evidence.getEvidenceId(),
                evidence.getOriginalFilename(),
                evidence.getContentType(),
                evidence.getFileSize(),
                evidence.getSortOrder()
        );
    }
}
