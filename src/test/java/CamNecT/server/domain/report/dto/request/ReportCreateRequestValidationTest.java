package CamNecT.server.domain.report.dto.request;

import CamNecT.server.domain.report.model.ReportCategory;
import CamNecT.server.domain.report.model.TargetType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCreateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsNonPositiveIdsAndValuesLargerThanDatabaseContract() {
        ReportCreateRequest request = new ReportCreateRequest(
                0L,
                -1L,
                TargetType.COMMUNITY,
                ReportCategory.OTHER,
                "t".repeat(256),
                "c".repeat(16001),
                null
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("reportedUserId", "reportedPostId", "title", "context");
    }
}
