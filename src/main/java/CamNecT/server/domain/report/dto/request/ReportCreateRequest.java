package CamNecT.server.domain.report.dto.request;

import CamNecT.server.domain.report.model.ReportCategory;
import CamNecT.server.domain.report.model.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReportCreateRequest(
        @NotNull @Positive Long reportedUserId,
        @Positive Long reportedPostId, // 유저 신고 시 null 가능
        @NotNull TargetType postType,
        @NotNull ReportCategory reportCategory,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 16000) String context,
        @Size(max = 5) List<@NotBlank @Size(max = 500) String> evidenceImageKeys
) {}
