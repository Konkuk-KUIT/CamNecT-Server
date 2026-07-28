package CamNecT.server.domain.report.dto.request;

import CamNecT.server.domain.report.model.ReportCategory;
import CamNecT.server.domain.report.model.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReportCreateRequest(
        @NotNull Long reportedUserId,
        Long reportedPostId, // 유저 신고 시 null 가능
        @NotNull TargetType postType,
        @NotNull ReportCategory reportCategory,
        @NotBlank String title,
        @NotBlank String context,
        String evidenceImageUrl // 증거 이미지 URL
) {}