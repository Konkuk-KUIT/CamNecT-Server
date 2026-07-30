package CamNecT.server.domain.report.dto.request;

import CamNecT.server.domain.report.model.ReportCategory;
import CamNecT.server.domain.report.model.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReportCreateRequest(
        @NotNull Long reportedUserId,
        Long reportedPostId, // 유저 신고 시 null 가능
        @NotNull TargetType postType,
        @NotNull ReportCategory reportCategory,
        @NotBlank String title,
        @NotBlank String context,
        List<@NotBlank @Size(max = 500) String> evidenceImageKeys
) {}
