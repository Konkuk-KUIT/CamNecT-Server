package CamNecT.server.domain.profile.components.education.dto.request;

import CamNecT.server.domain.profile.components.education.model.EducationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EducationRequest(
        @NotNull @Positive Long institutionId,
        @NotNull @Positive Long campusId,
//        Long majorId,
//        String degree,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull EducationStatus status,
        @Size(max = 16000) String description
) {
    @AssertTrue(message = "종료일은 시작일보다 빠를 수 없습니다.")
    @Schema(hidden = true)
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
