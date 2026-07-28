package CamNecT.server.domain.report.dto.request;

import CamNecT.server.domain.report.model.ReportStatus;
import CamNecT.server.domain.report.model.ReportCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


public record ReportProcessRequest(
        @NotNull ReportStatus status,
        ReportCategory decidedCategory,
        @Size(max = 500) String reason
) {
    public ReportStatus getStatus(){
        return status;
    }
}
