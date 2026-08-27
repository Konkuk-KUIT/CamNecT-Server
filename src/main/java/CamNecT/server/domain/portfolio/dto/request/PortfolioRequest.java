package CamNecT.server.domain.portfolio.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record PortfolioRequest(
        @NotBlank @Size(max = 100) String projectTitle,
        @Size(max = 50) String subtitle,
        @Size(max = 16000) String description,
        @NotNull LocalDate startedAt,
        LocalDate endedAt,
        @Size(max = 100) String project_role,
        @Size(max = 10) List<@NotBlank @Size(max = 20) String> techStack,
        @Size(max = 16000) String review,
        @Size(max = 500) String thumbnailKey,
        @Size(max = 10) List<@NotBlank @Size(max = 500) String> attachmentKeys
) {
    // null 방어: attachmentKeys가 null로 들어오면 빈 리스트로 초기화
    public PortfolioRequest {
        techStack = (techStack == null) ? new java.util.ArrayList<>() : new java.util.ArrayList<>(techStack);
    }

    @AssertTrue(message = "종료일은 시작일보다 빠를 수 없습니다.")
    @Schema(hidden = true)
    public boolean isDateRangeValid() {
        return startedAt == null || endedAt == null || !endedAt.isBefore(startedAt);
    }
}
